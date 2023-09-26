(ns bosquet.llm.openai
  (:require
   [bosquet.llm.chat :as chat]
   [bosquet.llm.llm :as llm]
   [clojure.string :as string]
   [jsonista.core :as j]
   [malli.generator :as mg]
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

(defn- ->completion*
  [content {:keys [prompt_tokens completion_tokens total_tokens]}]
  {llm/content content
   llm/usage   {:prompt     prompt_tokens
                :completion completion_tokens
                :total      total_tokens}})

(defmulti ->completion (fn [{object :object}] object))

(defmethod ->completion "chat.completion"
  [{:keys [choices usage]}]
  (let [{msg :message finish :finish_reason} (first choices)]
    (->completion*
     {:completion    (chat/chatml->bosquet msg)
      :finish-reason finish}
     usage)))

(defmethod ->completion "text_completion"
  [{:keys [choices usage]}]
  (let [{text :text probs :logprobs finish :finish_reason} (first choices)]
    (->completion*
     {:completion    text
      :logprobs      probs
      :finish-reason finish}
     usage)))

(def default-system-prompt
  {:role :system :content "You are a helpful assistant."})

(defn create-chat-completion
  "Completion using Chat GPT model. This one is loosing the conversation
  aspect of the API. It will construct basic `system` for the
  conversation and then use `prompt` as the `user` in the chat "
  [prompt params opts]
  (->completion
   (api/create-chat-completion
    (assoc params
           :messages [default-system-prompt
                      {:role :user :content prompt}])
    opts)))

(defn create-completion
  "Create completion (not chat) for `prompt` based on model `params` and invocation `opts`"
  [prompt params opts]
  (->completion
   (api/create-completion
    (assoc params :prompt prompt) opts)))

(defn- ->error [ex]
  (ex-info
   "Completion error in OAI call"
   (or
    (-> ex ex-data :body
        (j/read-value j/keyword-keys-object-mapper)
        :error)
      ;; Azure has different error data structure
    (ex-data ex))))

(defn complete
  "Complete `prompt` if passed in `model` is cGPT the completion will
  be passed to `complete-chat`"
  {:malli/schema
   [:function
    [:=> [:cat :string] llm/completion-response]
    [:=> [:cat :string :map] llm/completion-response]
    [:=> [:cat :string :map :map] llm/completion-response]]
   :malli/gen mg/generate}
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
         (throw (->error e)))))))

(defn chat-completion
  [messages {:keys [model] :as params} opts]
  (let [params   (if model params (assoc params :model cgpt-35))
        messages (mapv chat/bosquet->chatml messages)]
    (timbre/infof "Calling OAI chat with:")
    (timbre/infof "\tParams: '%s'" (dissoc params :prompt))
    (timbre/infof "\tConfig: '%s'" (dissoc opts :api-key))
    (-> params
        (assoc :messages messages)
        (api/create-chat-completion opts)
        ->completion)))

(deftype OpenAI
         [opts]
  llm/LLM
  (generate [_this prompt params]
    (complete prompt params opts))
  (chat     [_this conversation params]
    (chat-completion conversation params opts)))

(comment
  (chat-completion
   [(chat/speak chat/system "You are a helpful assistant.")
    (chat/speak chat/user "Who won the world series in 2020?")
    (chat/speak chat/assistant "The Los Angeles Dodgers won the World Series in 2020.")
    (chat/speak chat/user "Where was it played?")]
   nil nil)

  (complete "What is your name?" {:max-tokens 10 :model "gpt-4"})
  (complete "What is your name?" {:max-tokens 10 :model "x"})
  (complete "What is your name?" {:max-tokens 10 :model "text-ada-001"}))
