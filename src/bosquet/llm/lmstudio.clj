(ns bosquet.llm.lmstudio
  (:require
   [bosquet.llm.chat :as chat]
   [bosquet.llm.http :as http]
   [bosquet.llm.wkk :as wkk]
   [bosquet.utils :as u]
   [taoensso.timbre :as timbre]))

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

(defn chat
  ([params] (chat nil params))
  ([{api-endpoint :api-endpoint :as service-cfg}
    {messages :messages :as params}]
   (timbre/infof "💬 Calling LM Studio with:")
   (timbre/infof "\tParams: '%s'" (dissoc params :messages))
   (timbre/infof "\tConfig: '%s'" (dissoc service-cfg :api-key))
   (let [lm-call (partial http/post (str api-endpoint "/chat/completions"))]
     (-> params
         (assoc :messages messages)
         u/snake_case
         lm-call
         (->completion :chat)))))

(defn complete
  ([params] (chat nil params))
  ([{api-endpoint :api-endpoint :as service-cfg}
    {prompt :prompt :as params}]
   (timbre/infof "💬 Calling LM studio completion with:")
   (timbre/infof "\t* Params: '%s'" (dissoc params :prompt))
   (timbre/infof "\t* Options: '%s'" (dissoc service-cfg :api-key))
   (let [lm-call (partial http/post (str api-endpoint "/chat/completions"))
         params (-> params
                    (dissoc :prompt)
                    (assoc :messages [{:role :user :content prompt}]))]
     (-> params
         u/snake_case
         lm-call
         (->completion :completion)))))

(comment
  (complete
   {:api-endpoint "http://localhost:1234/v1"}
   {:prompt "2+2="})
  (chat
   {:api-endpoint "http://localhost:1234/v1"}
   {:messages [{:role :system :content "You are a calculator. You only converse in this format: expression = answer"}
               {:role :user :content "2-2="}]})
  #__)
