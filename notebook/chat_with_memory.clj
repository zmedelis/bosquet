(ns chat-with-memory
  (:require
   [bosquet.dataset.huggingface :as hfds]
   [bosquet.llm.chat :as chat]
   [bosquet.llm.generator :as gen]
   [bosquet.llm.llm :as llm]
   [bosquet.memory.memory :as m]
   [bosquet.memory.retrieval :as r]
   [bosquet.system :as system]
   [bosquet.wkk :as wkk]
   [clojure.string :as string]
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


(comment
  (hfds/download-ds
   {:dataset "allenai/prosocial-dialog"
    :split   "train"
    :config  "default"
    :offset  0
    :length  100}
   {:hfds/use-cache true
    :hfds/record-limit 1000}))


(def prosocial-dialog-dataset
  (hfds/load-ds "allenai/prosocial-dialog"))

(def dialog-ds-subset
  (filter #(zero? (:response_id %))
    prosocial-dialog-dataset))

^{::clerk/visibility {:code :hide}}
(clerk/table
  {:head ["Context" "Response" "RoTs"]
   :rows (mapv
           (fn [{:keys [context response rots]}]
             [(helpers/text-div context)
              (helpers/text-div response)
              (helpers/text-list rots)])
           dialog-ds-subset)})

(def params {chat/conversation {wkk/service          [:llm/openai :provider/openai]
                                wkk/model-parameters {:temperature 0
                                                      :max-tokens  200
                                                      :model       "gpt-3.5-turbo"}}})

(def mem-sys (system/get-memory wkk/simple-short-term-memory))

(def mem-config
  {wkk/memory-system     mem-sys
   wkk/recall-function   r/recall-sequential
   r/memory-tokens-limit 500
   r/memory-content-fn   :content})

;; Before running the chat session we need to forget whatever might have been
;; stored in the memory.

(.forget mem-sys {})

(doseq [prosoc-observation dialog-ds-subset]
  (.remember mem-sys prosoc-observation {}))


(defn chat-demo [queries params]
  (mapv
    (fn [q]
      (let [message  [(chat/speak chat/user q)]
            memories (m/available-memories mem-config message)
            response (gen/chat (concat memories message) {} params)]
        (.remember mem-sys message nil)
        (.remember mem-sys (-> response llm/content :completion) nil)
        {:question q
         :memories memories
         :response response}))
    queries))

(def resp (chat-demo (take 3 queries) params))

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
