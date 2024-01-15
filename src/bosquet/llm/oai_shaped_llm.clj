(ns bosquet.llm.oai-shaped-llm
  (:require
   [bosquet.llm.chat :as chat]
   [bosquet.llm.wkk :as wkk]))

(defn ->completion
  "Construct completion result data with generation
  `content` and token `usage`"
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



(comment
  #_(completion
   (chat/converse chat/user "Calculate: 1 + 2")
   {gen-type      :chat
    :model        "mistral-small"
    :api-key      (-> "config.edn" slurp read-string :mistral-api-key)
    :api-endpoint "https://api.mistral.ai/v1"})
  #__)
