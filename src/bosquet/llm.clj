(ns bosquet.llm
  (:require
   [bosquet.env :as env]
   [bosquet.llm.cohere :as cohere]
   [bosquet.llm.lmstudio :as lmstudio]
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
   :local       {wkk/complete-fn (fn [_system options] {:eval (str "TODO-" (:gen options) "-COMPLETE")})
                 wkk/chat-fn     (fn [_system options] {:eval (str "TODO-" (:gen options) "-CHAT")})}})
