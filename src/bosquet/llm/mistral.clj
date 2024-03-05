(ns bosquet.llm.mistral
  (:require
   [bosquet.env :as env]
   [bosquet.llm.oai-shaped-llm :as oai]
   [bosquet.llm.wkk :as wkk]))


(def default-model
  "Mistral model to be used when no model is provided in call config"
  :mistral-small)


(defn chat
  ([params] (chat (wkk/mistral env/config) params))
  ([service-cfg params]
   (oai/create-completion service-cfg
                          params
                          default-model)))


(defn complete
  ([params] (complete (wkk/mistral env/config) params))
  ([service-cfg {prompt :prompt :as params}]
   (oai/create-completion service-cfg
                          params
                          [{:role :user :content prompt}]
                          default-model)))


(comment
  (chat
   {:messages [{:role :user :content "2/2="}]
    wkk/model-params {:model :mistral-small-latest}})
  (complete
   {:prompt "2+2="
    wkk/model-params {:model :mistral-small-latest}})
  #__)
