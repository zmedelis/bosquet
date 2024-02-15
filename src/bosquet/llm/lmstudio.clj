(ns bosquet.llm.lmstudio
  (:require
   [bosquet.env :as env]
   [bosquet.llm.chat :as chat]
   [bosquet.llm.http :as http]
   [bosquet.llm.wkk :as wkk]
   [bosquet.utils :as u]))

(defn- ->completion
  [{choices :choices {prompt_tokens     :prompt_tokens
                      completion_tokens :completion_tokens
                      total_tokens      :total_tokens} :usage}
   generation-type]
  (let [result (-> choices first :message chat/chatml->bosquet)]
    {wkk/generation-type generation-type
     wkk/content         result
     wkk/usage           {:prompt     prompt_tokens
                          :completion completion_tokens
                          :total      total_tokens}}))

(defn- prep-params
  ;; TODO is it so copy/paste accross oai, mistral and this ns
  [params]
  (-> params
      (dissoc :prompt wkk/model-params)
      (merge (wkk/model-params params))))

(defn- call-fn [{:keys [api-endpoint api-key]}]
  (partial http/post (str api-endpoint "/chat/completions") api-key))

(defn chat
  ([params] (chat (wkk/lmstudio env/config) params))
  ([service-cfg params]
   (u/log-call service-cfg params "LM Studio chat")
   (let [lm-call (call-fn service-cfg)]
     (-> params
         prep-params
         lm-call
         (->completion :chat)))))

(defn complete
  ([params] (complete (wkk/lmstudio env/config) params))
  ([service-cfg {prompt :prompt :as params}]
   (u/log-call service-cfg params "LM Studio completion")
   (let [lm-call (call-fn service-cfg)]
     (-> params
         prep-params
         (assoc :messages [{:role :user :content prompt}])
         lm-call
         (->completion :completion)))))

(comment
  (complete
   {:prompt "2+2="})
  (chat
   {:messages [{:role :system :content "You are a calculator. You only converse in this format: expression = answer"}
               {:role :user :content "2-2="}]})
  #__)
