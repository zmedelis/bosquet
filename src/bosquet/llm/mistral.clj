(ns bosquet.llm.mistral
  (:require
   [bosquet.llm.chat :as chat]
   [bosquet.llm.http :as http]
   [taoensso.timbre :as timbre]))

(defn- ->completion
  [{choices :choices {prompt_tokens     :prompt_tokens
                      completion_tokens :completion_tokens
                      total_tokens      :total_tokens} :usage}
   generation-type]
  #_(let [result (-> choices first :message chat/chatml->bosquet)]
      {llm/generation-type generation-type
       llm/content         (if (= :chat generation-type)
                             result
                             {:completion (:content result)})
       llm/usage           {:prompt     prompt_tokens
                            :completion completion_tokens
                            :total      total_tokens}}))

(defn completion
  [messages {generation-type gen-type
             api-endpoint    :api-endpoint
             :as             params}]
  (timbre/infof "💬 Calling LM Studio with:")
  (timbre/infof "\tParams: '%s'" (dissoc params :prompt))
  #_(let [call (partial http/post (str api-endpoint "/chat/completions"))]
      (-> params
          (assoc :messages (if (string? messages)
                             [(chat/speak chat/user messages)]
                             messages))
          call
          (->completion generation-type))))

(comment
  #_(completion
     (chat/converse chat/user "Calculate: 1 + 2")
     {gen-type      :chat
      :model        "mistral-small"
      :api-key      (-> "config.edn" slurp read-string :mistral-api-key)
      :api-endpoint "https://api.mistral.ai/v1"})
  #__)
