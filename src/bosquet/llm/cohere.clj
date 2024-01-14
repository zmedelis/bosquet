(ns bosquet.llm.cohere
  (:require
   [bosquet.llm.schema :as schema]
   [bosquet.llm.wkk :as wkk]
   [bosquet.utils :as u]
   [cohere.client :as client]))

(defn- props->cohere
  "Convert general LLM model properties to Cohere specific ones."
  [{:keys [n stop max-tokens] :as props}]
  (u/mergex
   (dissoc  props :n :stop :max-tokens)
   {:max_tokens max-tokens}
   {:num_generations n}
   {:stop_sequences stop}))

(defn usage->canonical
  [{:keys [input_tokens output_tokens]}]
  {schema/usage-in-count input_tokens
   schema/usage-out-count output_tokens
   schema/usage-total-count (+ output_tokens input_tokens)})

(defn complete
  ([prompt opts]
   (let [{{usage :billed_units} :meta generations :generations}
         (client/generate (props->cohere (assoc opts :prompt prompt)))]
     {wkk/generation-type :completion
      wkk/content         {:completion (-> generations first :text)}
      wkk/usage           (usage->canonical usage)}))
  ([prompt]
   (complete prompt {})))

(defn chatml->cohere
  "Transform ChatML messages to the message data shape required by Cohere API"
  [messages]
  (mapv
   (fn [{:keys [role content]}]
     {:user_name role :text content})
   messages))

(defn chat
  ([messages] (chat {:messages messages} nil))
  ([{messages :messages :as params} _opts]
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

  (chat messages)

  (client/chat
   :chat_history (chatml->cohere [{:role :user :content "Let's do some calculations!"}
                                  {:role :chatbot :content "Certainly, I am happy to calculate"}])
   :message "2+2=")

  (client/generate (props->cohere
                    {:model          "command"
                     :prompt  "Today is a"
                     :n              1
                     :stop-sequences ["\n"]
                     :temperature    0.2}))
  (complete
   "A party is about to begin."
   {:model "command"
    :n 2
    :stop-sequences ["\n"]
    :temperature 0.2}))
