(ns bosquet.llm.openai
  (:require
   [bosquet.env :as env]
   [bosquet.llm.oai-shaped-llm :as oai]
   [bosquet.llm.wkk :as wkk]
   [bosquet.utils :as u]
   [wkok.openai-clojure.api :as api]
   [net.modulolotus.truegrit.circuit-breaker :as cb]))


(def chat*
  (cb/wrap (fn [{url :api-endpoint default-params :model-params :as service-cfg} params]
             (u/log-call url params)
             (-> params
                 (oai/prep-params default-params)
                 (api/create-chat-completion service-cfg)
                 oai/->completion))
            u/rest-service-cb))


(defn chat
  "Run 'chat' type completion. Pass in `messages` in ChatML format."
  ([service-cfg params] (chat* service-cfg params))
  ([params] (chat (wkk/openai env/config) params)))


(def complete*
  "Run 'completion' type generation. `params` needs to have `prompt` key."
  (cb/wrap (fn [{url :api-endpoint default-params :model-params :as service-cfg} params]
             (u/log-call url params)
             (-> params
                 (oai/prep-params default-params)
                 (api/create-completion service-cfg)
                 oai/->completion))
           u/rest-service-cb))

(defn complete
  ([service-cfg params] (complete* service-cfg params))
  ([params] (complete (wkk/openai env/config) params)))

(comment
  (chat {:messages [{:role :user :content "2/2="}]})
  (complete {:prompt "2+2=" wkk/model-params {:model :davinci-002}})
  #__)
