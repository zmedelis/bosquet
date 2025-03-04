(ns bosquet.llm.localai
  (:require
   [bosquet.env :as env]
   [bosquet.llm.oai-shaped-llm :as oai]
   [bosquet.llm.wkk :as wkk]
   [bosquet.utils :as u]
   [bosquet.llm.http :as http]
   [wkok.openai-clojure.api :as api]))

(defn chat
  "Run 'chat' type completion. Pass in `messages` in ChatML format."
  ([params] (chat (wkk/localai env/config) params))
  ([{url :api-endpoint default-params :model-params :as service-cfg} params]
   (u/log-call url params)
   (-> params
       (oai/prep-params default-params)
       (api/create-chat-completion service-cfg)
       oai/->completion)))

(defn http-call
  "Run 'chat' type completion. Pass in `messages` in ChatML format."
  ([params] (http-call (wkk/localai env/config) params))
  ([{url :api-endpoint default-params :model-params :as service-cfg} params]
   (http/resilient-post (str url "/chat/completions") params)))

(defn complete
  "Run 'completion' type generation.
                         `params` needs to have `prompt` key.
                       
                         *Deprecated* by OAI?"
  ([params] (complete (wkk/localai env/config) params))
  ([{url :api-endpoint default-params :model-params :as service-cfg} params]
   (u/log-call url params)
   (-> params
       (oai/prep-params default-params)
       (api/create-completion service-cfg)
       oai/->completion)))


(comment
  (chat {:messages [{:role :user :content "2/2="}]})
  (complete {:prompt "2+2=" wkk/model-params {:model :phi-4}})
  (complete {:prompt "HOw are you doing?" wkk/model-params {:model :phi-4}})
  (http-call {:prompt "2+2=" wkk/model-params {:model :phi-4}})
  #__)
