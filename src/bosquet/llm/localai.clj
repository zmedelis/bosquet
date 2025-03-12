(ns bosquet.llm.localai
  (:require
   [bosquet.env :as env]
   [bosquet.llm.oai-shaped-llm :as oai]
   [bosquet.llm.wkk :as wkk]
   [bosquet.utils :as u]
   [wkok.openai-clojure.api :as api]
   [net.modulolotus.truegrit.circuit-breaker :as cb]))

(def chat*
  "Run 'chat' type completion. Pass in `messages` in ChatML format."
  (cb/wrap (fn [{url :api-endpoint default-params :model-params :as service-cfg} params]
             (u/log-call url params)
             (-> params
                 (oai/prep-params default-params)
                 (api/create-chat-completion service-cfg)
                 oai/->completion))
           u/rest-service-cb))

(defn chat [params]
  (chat* (wkk/localai env/config) params))

(def complete*
  "Run 'completion' type generation.
                         `params` needs to have `prompt` key.
                       
                         *Deprecated* by OAI?"
  (cb/wrap (fn [{url :api-endpoint default-params :model-params :as service-cfg} params]
             (u/log-call url params)
             (-> params
                 (oai/prep-params default-params)
                 (api/create-completion service-cfg)
                 oai/->completion))
           u/rest-service-cb))

(defn complete [params]
  (complete* (wkk/localai env/config) params))

(comment
  (chat {:messages [{:role :user :content "2/2="}]}) 
  (complete {:prompt "2+2=" wkk/model-params {:model :phi-4}})
  (complete {:prompt "HOw are you doing?" wkk/model-params {:model :phi-4}})
  #__)
