(ns bosquet.llm.mistral
  (:require
   [bosquet.env :as env]
   [bosquet.llm.http :as http]
   [bosquet.llm.oai-shaped-llm :as oai]
   [bosquet.llm.wkk :as wkk]))


(defn- call-fn [{:keys [api-endpoint api-key]}]
  (partial http/post (str api-endpoint "/chat/completions") api-key))


(def default-model
  "Mistral model to be used when no model is provided in call config"
  :mistral-small)


(defn chat
  ([params] (chat (wkk/mistral env/config) params))
  ([service-cfg params]
   (let [lm-call (call-fn service-cfg)]
     (-> params
         (oai/prep-params default-model)
         lm-call
         (oai/->completion)))))


(defn complete
  ([params] (complete (wkk/mistral env/config) params))
  ([service-cfg {prompt :prompt :as params}]
   (let [lm-call (call-fn service-cfg)]
     (-> params
         (oai/prep-params default-model)
         (assoc :messages [{:role :user :content prompt}])
         lm-call
         (oai/->completion)))))


(comment
  (chat
   {:messages [{:role :user :content "2/2="}]
    wkk/model-params {:model :mistral-small-latest}})
  (complete
   {:prompt "2+2="
    wkk/model-params {:model :mistral-small-latest}})
  #__)
