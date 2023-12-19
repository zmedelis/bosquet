(ns bosquet.llm
  (:require
   [bosquet.llm.chat :as chat]
   [bosquet.llm.openai :as openai]))

(def openai ::openai)

(def cohere ::cohere)

(def lm-studio ::lm-studio)

(def service :llm/service)
(def model-params :llm/model-params)
(def gen-fn :llm/gen-fn)

(defn- handle-openai-chat [service-config params]
  (openai/chat-completion params service-config))

(defn- handle-cohere-chat [arg1]
  )

(defn- handle-lm-studio-chat [arg1]
  )

(def chat-handlers
  {openai    handle-openai-chat
   cohere    handle-cohere-chat
   lm-studio handle-lm-studio-chat})

(defn chat
  ([llm-service-config generation-props]
   (chat llm-service-config generation-props chat-handlers))

  ([llm-config {llm service :as generation-props} handlers]
   (let [handler (handlers llm)]
     (handler llm-config
              (dissoc generation-props service)))))


(comment
  (chat
   {:api-key      (-> "config.edn" slurp read-string :openai-api-key)
    :api-endpoint "https://api.openai.com/v1"}
   {service       openai
    :model       :gpt-3.5-turbo
    :messages    (chat/converse chat/user "What is the distance from Moon to Io?")
    :max-tokens  100
    :temperature 0.0})
  #__)
