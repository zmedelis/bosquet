(ns bosquet.llm.cohere
  (:require
   [bosquet.env :as env]
   [bosquet.llm.schema :as schema]
   [bosquet.llm.wkk :as wkk]
   [bosquet.utils :as u]
   [cohere.client :as client]))

(defn- set-api-key [api-key]
  (System/setProperty "cohere.api.key" api-key))

(defn- props->cohere
  "Convert general LLM model properties to Cohere specific ones."
  [{:keys [n stop] :as props}]
  (u/snake_case
   (u/mergex
    (dissoc  props :n :stop)
    {:num_generations n}
    {:stop_sequences stop})))

(defn usage->canonical
  [{:keys [input_tokens output_tokens]}]
  {schema/usage-in-count input_tokens
   schema/usage-out-count output_tokens
   schema/usage-total-count (+ output_tokens input_tokens)})

(defn complete
  ([{api-key :api-key :as cfg} params]
   (set-api-key api-key)
   (u/log-call cfg params "Cohere completion")
   (let [{{usage :billed_units} :meta generations :generations}
         (client/generate (props->cohere params))]
     {wkk/generation-type :completion
      wkk/content         {:completion (-> generations first :text)}
      wkk/usage           (usage->canonical usage)}))
  ([params]
   (complete (wkk/cohere env/config) params)))

(defn chatml->cohere
  "Transform ChatML messages to the message data shape required by Cohere API"
  [messages]
  (mapv
   (fn [{:keys [role content]}]
     {:user_name role :text content})
   messages))

(defn chat
  ([params] (chat (wkk/cohere env/config) params))
  ([{api-key :api-key :as cfg} {messages :messages :as params}]
   (set-api-key api-key)
   (u/log-call cfg params "Cohere chat")
   (let [params   (dissoc params :messages)
         messages (chatml->cohere messages)
         message  (-> messages last :text)
         history  (butlast messages)
         {{usage :billed_units} :meta text :text}
         (client/chat
          (props->cohere (assoc params
                                :message message
                                :chat_history history)))]
     {wkk/generation-type :chat
      wkk/content         {:role :assistant :content text}
      wkk/usage           (usage->canonical usage)})))

(comment
  (def messages [{:role :user :content "Let's do some calculations!"}
                 {:role :chatbot :content "Certainly, I am happy to calculate"}
                 {:role :user :content "4+4="}])

  (client/chat
   :chat_history (chatml->cohere [{:role :user :content "Let's do some calculations!"}
                                  {:role :chatbot :content "Certainly, I am happy to calculate"}])
   :message "2+2=")

  (client/generate (props->cohere
                    {:model          "command"
                     :prompt         "Today is a"
                     :n              1
                     :stop-sequences ["\n"]
                     :temperature    0.2}))
  (complete
   {:prompt         "A party is about to begin."
    :model          "command"
    :n              1
    :stop-sequences ["\n"]
    :temperature    0.2}))
