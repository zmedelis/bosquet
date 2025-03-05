(ns bosquet.llm.claude
  (:require
   [bosquet.env :as env]
   [bosquet.llm.http :as http]
   [bosquet.llm.wkk :as wkk]
   [bosquet.llm.schema :as schema]
   [bosquet.utils :as u]))


(defn usage->canonical
  [{:keys [input_tokens output_tokens]}]
  {schema/usage-in-count input_tokens
   schema/usage-out-count output_tokens
   schema/usage-total-count (+ output_tokens input_tokens)})


(defn- header [key]
  {:headers {"x-api-key"         key
             "anthropic-version" "2023-06-01"}})


(defn messages
  ([params] (messages (wkk/openai env/config) params))
  ([{key :api-key url :api-endpoint} params]
   (u/log-call url params)
   (let [{:keys [content usage]} (http/resilient-post (str url "/messages") (header key) params)]
     {wkk/generation-type :chat
      wkk/content         {:role    :assistant
                           :content (-> content last :text)}
      wkk/usage           (usage->canonical usage)})))
