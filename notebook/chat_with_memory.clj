(ns chat-with-memory
  (:require
    [bosquet.llm.chat :as chat]
    [bosquet.llm.generator :as gen]
    [bosquet.llm.llm :as llm]
    [bosquet.memory.retrieval :as r]
    [bosquet.system :as system]
    [bosquet.wkk :as wkk]
    helpers
    [nextjournal.clerk :as clerk]))

;; Example taken from
;; https://github.com/pinecone-io/examples/blob/master/learn/generation/langchain/handbook/03a-token-counter.ipynb

(def queries
  ["Good morning AI?"
   "My interest here is to explore the potential of integrating Large Language Models with external knowledge"
   "I just want to analyze the different possibilities. What can you think of?"
   "What about the use of retrieval augmentation, can that be used as well?"
   "That's very interesting, can you tell me more about this? Like what systems would I use to store the information and retrieve relevant info?"
   "Okay that's cool, I've been hearing about 'vector databases', are they relevant in this context?"
   "Okay that's useful, but how do I go from my external knowledge to creating these 'vectors'? I have no idea how text can become a vector?"
   "Well I don't think I'd be using word embeddings right? If I wanted to store my documents in this vector database, I suppose I would need to transform the documents into vectors? Maybe I can use the 'sentence embeddings' for this, what do you think?"
   "Can sentence embeddings only represent sentences of text? That seems kind of small to capture any meaning from a document? Is there any approach that can encode at least a paragraph of text?"
   "Huh, interesting. I do remember reading something about 'mpnet' or 'minilm' sentence 'transformer' models that could encode small to medium sized paragraphs. Am I wrong about this?"
   "Ah that's great to hear, do you happen to know how much text I can feed into these types of models?"
   "I've never heard of hierarchical embeddings, could you explain those in more detail?"
   "So is it like you have a transformer model or something else that creates sentence level embeddings, then you feed all of the sentence level embeddings into another separate neural network that knows how to merge multiple sentence embeddings into a single embedding?"
   "Could you explain this process step by step from start to finish? Explain like I'm very new to this space, assume I don't have much prior knowledge of embeddings, neural nets, etc"
   "Awesome thanks! Are there any popular 'heirarchical neural network' models that I can look up? Or maybe just the second stage that creates the hierarchical embeddings?"
   "It seems like these HAN models are quite old, is there anything more recent?"
   "Can you explain the difference between transformer-XL and longformer?"
   "How much text can be encoded by each of these models?"
   "Okay very interesting, so before returning to earlier in the conversation. I understand now that there are a lot of different transformer (and not transformer) based models for creating the embeddings from vectors. Is that correct?"
   "Perfect, so I understand text can be encoded into these embeddings. But what then? Once I have my embeddings what do I do?"])


(def params {chat/conversation
             {wkk/memory-type       :memory/simple-short-term
              wkk/recall-function   (fn [memory-system _messages params]
                                      (.sequential-recall memory-system params))
              wkk/memory-parameters {r/memory-tokens-limit 3000}
              wkk/service           [:llm/openai :provider/openai]
              wkk/model-parameters  {:temperature 0
                                     :max-tokens  100
                                     :model       "gpt-3.5-turbo"}}})

;; Before running the chat session we need to forget whatever might have been
;; stored in the memory.

(def mem (system/get-memory (wkk/memory-type params)))
(.forget mem {})

(defn chat-demo [queries params]
  (gen/chat [(chat/speak chat/system "You are a brilliant assistant")] {} params)
  (map
    (fn [q]
      (let [message  [(chat/speak chat/user q)]]
        {:question q
         :memories (gen/available-memories message [chat/conversation] params)
         :response (gen/chat message {} params)}))
    queries))

(def resp (chat-demo (take 5 queries) params))

(.forget mem {})

(def resp-long-term-mem
  (chat-demo (take 5 queries)
    (assoc
      params
      wkk/memory-type :memory/simple-short-term
      wkk/recall-function :memory.racall/by-cue
      #_(fn [memory-system messages params]
        (.cue-recall memory-system (last messages) params)))))

^{:nextjournal.clerk/visibility {:code :fold}}
(clerk/table
  {:head ["Request" "Memories"]
   :rows (mapv (fn [{:keys [question memories response]}]
                 [(clerk/html [:div
                               (helpers/kv-cell "Question" question)
                               (helpers/chatml-cell (llm/gen-content response))])
                  (clerk/html
                    (vec (cons :div (mapv helpers/chatml-cell memories))))])
           resp)})
