(ns bosquet.llm.lmstudio
  (:require
   [bosquet.llm.chat :as chat]
   [bosquet.llm.llm :as llm]
   [bosquet.llm.http :as http]
   [taoensso.timbre :as timbre]))

(defn- ->completion
  [{choices :choices {prompt_tokens     :prompt_tokens
                      completion_tokens :completion_tokens
                      total_tokens      :total_tokens} :usage}
   generation-type]
  (let [result (-> choices first :message chat/chatml->bosquet)]
    {llm/generation-type generation-type
     llm/content         (if (= :chat generation-type)
                           result
                           {:completion (:content result)})
     llm/usage           {:prompt     prompt_tokens
                          :completion completion_tokens
                          :total      total_tokens}}))

(def ^:private gen-type :gen-type)

(defn- chat-completion
  [messages {generation-type gen-type
             api-endpoint    :api-endpoint
             :as             params}]
  (timbre/infof "💬 Calling LM Studio with:")
  (timbre/infof "\tParams: '%s'" (dissoc params :prompt))
  (let [call (partial http/post (str api-endpoint "/chat/completions"))]
    (-> params
        (assoc :messages (if (string? messages)
                           [(chat/speak chat/user messages)]
                           messages))
        call
        (->completion generation-type))))

(deftype LMStudio
         [opts]
  llm/LLM
  (service-name [_this] ::lm-studio)
  (generate [_this prompt params]
    (chat-completion prompt
                     (merge opts
                            (assoc params gen-type :complete))))
  (chat     [_this conversation params]
    (chat-completion conversation
                     (merge opts
                            (assoc params gen-type :chat)))))

(comment
  (def llm (LMStudio.
            {:api-endpoint "http://localhost:1234/v1"}))
  (.chat llm
         (chat/converse chat/system "You are good at maths."
                        chat/user "1 + 2 =")
         {})
  #__)
