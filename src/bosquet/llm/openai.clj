(ns bosquet.llm.openai
  (:require
   [bosquet.llm.chat :as llm.chat]
   [bosquet.llm.llm :as llm]
   [clojure.string :as string]
   [jsonista.core :as j]
   [taoensso.timbre :as timbre]
   [wkok.openai-clojure.api :as api]))

#_:clj-kondo/ignore
(def ada "text-ada-001")

#_:clj-kondo/ignore
(def davinci "text-davinci-003")

#_:clj-kondo/ignore
(def cgpt-35 "gpt-3.5-turbo")

(defn chat-model?
  "Check if `model` (name provided as string) is a chat model"
  [model]
  (string/starts-with? model "gpt-"))

(defn create-chat-completion
  "Completion using Chat GPT model. This one is loosing the conversation
  aspect of the API. It will construct basic `system` for the
  conversation and then use `prompt` as the `user` in the chat "
  [prompt params opts]
  (-> (api/create-chat-completion
       (assoc params
              :messages [{:role "system" :content "You are a helpful assistant."}
                         {:role "user" :content prompt}])
       opts)
      :choices first :message :content))

(defn create-completion
  "Create completion (not chat) for `prompt` based on model `params` and invocation `opts`"
  [prompt params opts]
  (-> (api/create-completion
       (assoc params :prompt prompt) opts)
      :choices first :text))

(defn complete
  "Complete `prompt` if passed in `model` is cGPT the completion will
  be passed to `complete-chat`"
  ([prompt] (complete prompt nil nil))
  ([prompt params] (complete prompt params nil))
  ([prompt {:keys [model]
            :or   {model cgpt-35}
            :as   params} opts]
   (let [params (if (nil? params) {:model model} (assoc params :model model))]
     (timbre/infof "Calling OAI completion with:")
     (timbre/infof "\tParams: '%s'" (dissoc params :prompt))
     (timbre/infof "\tOptions: '%s'" (dissoc opts :api-key))
     (try
       (if (chat-model? model)
         (create-chat-completion prompt params opts)
         (create-completion prompt params opts))
       (catch Exception e
         (throw
          (ex-info "OpenAI API error"
                   (or
                    (-> e ex-data :body
                        (j/read-value j/keyword-keys-object-mapper)
                        :error)
               ;; Azure has different error data structure
                    (ex-data e)))))))))

(def ^:private bosquet-chatml-roles
  {llm.chat/system    :system
   llm.chat/user      :user
   llm.chat/assistant :assistant})

(def ^:private chatml-bosquet-roles
  {"system"    llm.chat/system
   "user"      llm.chat/user
   "assistant" llm.chat/assistant})

(defn- bosquet->chatml
  [{role llm.chat/role content llm.chat/content}]
  {:role (bosquet-chatml-roles role) :content content})

(defn- chatml->bosquet
  [{:keys [role content]}]
  {llm.chat/role (chatml-bosquet-roles role) llm.chat/content content})

(defn chat-completion
  [messages {:keys [model] :as params} opts]
  (let [params (if model params (assoc params :model cgpt-35))]
    (timbre/infof "Calling OAI chat with:")
    (timbre/infof "\tParams: '%s'" (dissoc params :prompt))
    (timbre/infof "\tConfig: '%s'" (dissoc opts :api-key))
    (-> (assoc params :messages (mapv bosquet->chatml messages))
        (api/create-chat-completion opts)
        :choices first :message chatml->bosquet)))

(deftype OpenAI
         [opts]
  llm/LLM
  (generate [_this prompt params]
    (complete prompt params opts))
  (chat     [_this conversation params]
    (chat-completion conversation params opts)))

(comment
  (chat-completion
   [{llm.chat/role :system llm.chat/content "You are a helpful assistant."}
    {llm.chat/role :user llm.chat/content "Who won the world series in 2020?"}
    {llm.chat/role :assistant llm.chat/content "The Los Angeles Dodgers won the World Series in 2020."}
    {llm.chat/role :user llm.chat/content "Where was it played?"}]
   {:model "gpt-3.5-turbo"}
   nil)

  (complete "What is your name?" {:max-tokens 10 :model "gpt-4"})
  (complete "What is your name?" {:max-tokens 10 :model :ccgpt})
  (complete "What is your name?" {:max-tokens 10}))
