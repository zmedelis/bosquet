(ns bosquet.llm.openai
  (:require
   [bosquet.llm.llm :as llm]
   [clojure.string :as string]
   [jsonista.core :as j]
   [taoensso.timbre :as timbre]
   [wkok.openai-clojure.api :as api]))

(def ada "text-ada-001")

#_:clj-kondo/ignore
(def davinci "text-davinci-003")

#_:clj-kondo/ignore
(def cgpt-35 "gpt-3.5-turbo")

(defn chat-model?
  "Check if `model` (name provided as string) is a chat model"
  [model]
  (string/starts-with? model "gpt-"))

(defn- create-chat
  "Completion using Chat GPT model. This one is loosing the conversation
  aspect of the API. It will construct basic `system` for the
  conversation and then use `prompt` as the `user` in the chat "
  [prompt params opts]
  (-> (api/create-chat-completion
       (assoc params
              :model cgpt-35
              :messages [{:role "system" :content "You are a helpful assistant."}
                         {:role "user" :content prompt}])
       opts)
      :choices first :message :content))

(defn- create-completion
  "Create completion (not chat) for `prompt` based on model `params` and invocation `opts`"
  [prompt params opts]
  (-> (api/create-completion
       (assoc params :prompt prompt) opts)
      :choices first :text))

(defn complete
  "Complete `prompt` if passed in `model` is cGPT the completion will
  be passed to `complete-chat`"
  ([prompt] (complete prompt nil))
  ([prompt {:keys [impl api-key
                   api-endpoint
                   model temperature max-tokens n top-p
                   presence-penalty frequence-penalty]
            :or   {impl              :openai
                   model             cgpt-35
                   temperature       0.2
                   max-tokens        250
                   presence-penalty  0.4
                   frequence-penalty 0.2
                   top-p             1
                   n                 1}}]
   (let [params {:model             model
                 :temperature       temperature
                 :max_tokens        max-tokens
                 :presence_penalty  presence-penalty
                 :frequency_penalty frequence-penalty
                 :n                 n
                 :top_p             top-p
                 :prompt            prompt}
         opts   {:api-key api-key
                 :impl impl
                 :api-endpoint api-endpoint}]
     (timbre/infof "Calling OAI with:")
     (timbre/infof "\tParams: '%s'" (dissoc params :prompt))
     (timbre/infof "\tConfig: '%s'" (dissoc opts :api-key))

     (try
       (if (chat-model? model)
         (create-chat prompt params opts)
         (create-completion prompt params opts))
       (catch Exception e
         (throw
          (ex-info "OpenAI API error"
            (or
                (-> e ex-data :body
                    (j/read-value j/keyword-keys-object-mapper)
                    :error)
                ;; Azure has different error data structure
                {:message (.getMessage e)}))))))))

(defn chat-completion
  [messages opts]
  (-> (assoc opts :messages messages)
      api/create-chat-completion
      :choices first :message))

(deftype OpenAI
         [config]
  llm/LLM
  (generate [_this prompt props]
    (complete prompt (merge config props)))
  (chat     [_this conversation props]
    (chat-completion conversation (merge config props))))

(comment
  (chat-completion
   [{:role :system :content "You are a helpful assistant."}
    {:role :user :content "Who won the world series in 2020?"}
    {:role :assistant :content "The Los Angeles Dodgers won the World Series in 2020."}
    {:role :user :content "Where was it played?"}]
   {:model "gpt-3.5-turbo"})

  (complete "What is your name?" {:max-tokens 10 :model "gpt-3.5-turbo"})
  (complete "What is your name?" {:max-tokens 10 :model :ccgpt})
  (complete "What is your name?" {:max-tokens 10}))
