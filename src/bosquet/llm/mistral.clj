(ns bosquet.llm.mistral
  (:require
   [bosquet.env :as env]
   [bosquet.llm.http :as http]
   [bosquet.llm.oai-shaped-llm :as oai]
   [bosquet.llm.wkk :as wkk]
   [bosquet.utils :as u]))

(defn chat
  ([params] (chat (wkk/mistral env/config) params))
  ([{api-endpoint :api-endpoint :as service-cfg}
    {messages :messages :as params}]
   (u/log-call service-cfg params "Mistral chat")
   (let [lm-call (partial http/post (str api-endpoint "/chat/completions"))]
     (-> params
         (assoc :messages messages)
         u/snake_case
         lm-call
         (oai/->completion :chat)))))

(defn complete
  ([params] (complete
             (wkk/mistral env/config)
             params))
  ([{api-endpoint :api-endpoint :as service-cfg}
    {prompt :prompt :as params}]
   (u/log-call service-cfg params "Mistral completion")
   (let [lm-call (partial http/post (str api-endpoint "/chat/completions"))
         params (-> params
                    (dissoc :prompt)
                    (assoc :messages [{:role :user :content prompt}]))]
     (-> params
         u/snake_case
         lm-call
         (oai/->completion :completion)))))

(comment
  (complete
   {:messages [:role :user :content "2+2="]
    wkk/model-params {:model "mistral-small"}})
  #__)
