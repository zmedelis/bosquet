(ns bosquet.llm
  (:require
   [bosquet.env :as env]
   [bosquet.llm.cohere :as cohere]
   [bosquet.llm.lmstudio :as lmstudio]
   [bosquet.llm.mistral :as mistral]
   [bosquet.llm.openai :as openai]
   [bosquet.llm.wkk :as wkk]))

(def default-services
  {wkk/lmstudio (merge (env/val wkk/lmstudio)
                       {wkk/complete-fn lmstudio/complete
                        wkk/chat-fn     lmstudio/chat})
   wkk/openai   (merge (env/val wkk/openai)
                       {wkk/complete-fn openai/complete
                        wkk/chat-fn     openai/chat})
   wkk/cohere   (merge (env/val wkk/cohere)
                       {wkk/complete-fn cohere/complete
                        wkk/chat-fn     cohere/chat})
   wkk/mistral  (merge (env/val wkk/mistral)
                       {wkk/complete-fn mistral/complete
                        wkk/chat-fn     mistral/chat})})
