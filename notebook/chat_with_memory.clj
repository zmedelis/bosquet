(ns chat-with-memory
  (:require
   [bosquet.llm.chat :as chat]
   [bosquet.llm.generator :as gen]
   [bosquet.memory.retrieval :as r]
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
   "Perfect, so I understand text can be encoded into these embeddings. But what then? Once I have my embeddings what do I do?"
   "I'd like to use these embeddings to help a chatbot or a question-answering system answer questions with help from this external knowledge base. I suppose this would come under information retrieval? Could you explain that process in a little more detail?"
   "Okay great, that sounds like what I'm hoping to do. When you say the 'chatbot or question-answering system generates an embedding', what do you mean exactly?"
   "Ah okay, I understand, so it isn't the 'chatbot' model specifically creating the embedding right? That's how I understood your earlier comment. It seems more like there is a separate embedding model? And that encodes the query, then we retrieve the set of relevant documents from the external knowledge base? How is that information then used by the chatbot or question-answering system exactly?"
   "Okay but how is the information provided to the chatbot or question-answering system?"
   "So the retrieved information is given to the chatbot / QA system as plain text? But then how do we pass in the original query? How can the system distinguish between a user's query and all of this additional information?"
   "That doesn't seem correct to me, my question is — if we are giving the chatbot / QA system the user's query AND retrieved information from an external knowledge base, and it's all fed into the model as plain text, how does the model know what part of the plain text is a query vs. retrieved information?"
   "Yes I get that, but in the text passed to the model, how do we identify user prompt vs retrieved information?"])


(def params {chat/conversation
             {:bosquet.memory/type          :memory/simple-short-term
              :bosquet.memory/parameters    {r/memory-tokens-limit 3000}
              :bosquet.llm/service          [:llm/openai :provider/openai]
              ;; TODO rename `model-parameters` -> `parameters`
              :bosquet.llm/model-parameters {:temperature 0
                                             :max-tokens  100
                                             :model       "gpt-3.5-turbo"}}})

(def inputs {})

(defn chat-demo [queries]
  (gen/chat [(chat/speak chat/system "You are a brilliant assistant")] inputs params)
  ;; TODO `chat/speak` can be called inside `chat` simplify f signature
  ;; (defn chat [role content role content ...] inputs params)
  (map
    (fn [q]
      (let [message  [(chat/speak chat/user q)]
            memories (gen/available-memories message params)
            response (gen/chat message inputs params)
            result   {:question q
                      :memories memories
                      :response response}]
        (tap> result)
        result))
    queries))

(def resp (chat-demo (take 2 queries)))

(clerk/html
  [:div
   (mapv (fn [{:keys [question memories response]}]
           (let [{:keys [role content]} (get-in response [:bosquet.llm.llm/content :completion])]
             [:div
              [:div.flex.space-x-6
               [:p "Question"]
               [:p question]]
              [:div.flex.space-x-6
               [:p "Response"]
               [:p (str role ": " content)]]]))
     resp)])
