(ns bosquet.llm.lmstudio
  (:require
   [bosquet.env :as env]
   [bosquet.llm.chat :as chat]
   [bosquet.llm.http :as http]
   [bosquet.llm.oai-shaped-llm :as oai]
   [bosquet.llm.wkk :as wkk]
   [bosquet.utils :as u]))

(defn- call-fn [{:keys [api-endpoint api-key]}]
  (partial http/post (str api-endpoint "/chat/completions") api-key))

(defn chat
  ([params] (chat (wkk/lmstudio env/config) params))
  ([service-cfg params]
   (u/log-call service-cfg params "LM Studio chat")
   (let [lm-call (call-fn service-cfg)]
     (-> params
         oai/prep-params
         lm-call
         (chat/->completion :chat)))))

(defn complete
  ([params] (complete (wkk/lmstudio env/config) params))
  ([service-cfg {prompt :prompt :as params}]
   (u/log-call service-cfg params "LM Studio completion")
   (let [lm-call (call-fn service-cfg)]
     (-> params
         oai/prep-params
         (assoc :messages [{:role :user :content prompt}])
         lm-call
         (chat/->completion :completion)))))

(comment
  (complete {:prompt "2+2="})
  (chat
   {:messages [{:role :system :content "You are a calculator. You only converse in this format: expression = answer"}
               {:role :user :content "2-2="}]})
  #__)
