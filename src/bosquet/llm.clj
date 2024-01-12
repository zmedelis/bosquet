(ns bosquet.llm
  (:require
   [bosquet.llm.wkk :as wkk]
   [bosquet.env :as env]
   [bosquet.llm.cohere :as cohere]
   [bosquet.llm.openai :as openai]))

(defn handle-openai-chat [service-config params]
  (openai/chat-completion params service-config))

(defn handle-openai-complete [service-config params]
  (openai/chat-completion params service-config))

(defn handle-cohere-chat [service-config params]
  (cohere/complete params service-config))

(defn handle-cohere-complete [service-config params]
  (cohere/complete params service-config))

(defn- handle-lm-studio-chat [arg1])

(def default-services
  {wkk/openai (merge (env/val wkk/openai)
                     {wkk/complete-fn handle-openai-complete
                      wkk/chat-fn     handle-openai-chat})
   wkk/cohere (merge (env/val wkk/cohere)
                     {wkk/complete-fn handle-cohere-complete
                      wkk/chat-fn     handle-cohere-chat})
   :local     {wkk/complete-fn (fn [_system options] {:eval (str "TODO-" (:gen options) "-COMPLETE")})
               wkk/chat-fn     (fn [_system options] {:eval (str "TODO-" (:gen options) "-CHAT")})}})
