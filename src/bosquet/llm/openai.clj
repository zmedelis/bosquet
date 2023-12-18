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
  (string/starts-with? (name model) "gpt-"))

(defn- ->completion*
  [generation-type content {:keys [prompt_tokens completion_tokens total_tokens]}]
  {llm/generation-type generation-type
   llm/content         content
   llm/usage           {:prompt     prompt_tokens
                        :completion completion_tokens
                        :total      total_tokens}})

(defmulti ->completion (fn [{object :object}] object))

(defmethod ->completion "chat.completion"
  [{:keys [choices usage]}]
  (let [{msg :message finish :finish_reason} (first choices)]
    (->completion*
     :chat
     {:completion    (chat/chatml->bosquet msg)
      :finish-reason finish}
     usage)))

(defmethod ->completion "text_completion"
  [{:keys [choices usage]}]
  (let [{text :text probs :logprobs finish :finish_reason} (first choices)]
    (->completion*
     :completion
     {:completion    text
      :logprobs      probs
      :finish-reason finish}
     usage)))

;; TODO get rid of default system prompt
(def default-system-prompt
  {:role :system :content "You are a helpful assistant."})

(defn create-chat-completion
  "Completion using Chat GPT model. This one is loosing the conversation
  aspect of the API. It will construct basic `system` for the
  conversation and then use `prompt` as the `user` in the chat "
  [prompt params opts]
  (let [result (->completion
                (api/create-chat-completion
                 (assoc params
                        :messages [default-system-prompt
                                   {:role :user :content prompt}])
                 opts))]
    ;; wrangle the resulting data structure into `completion` format
    (-> result
        (assoc-in [llm/generation-type] :completion)
        (assoc-in [llm/content :completion] (-> result llm/content :completion :content)))))

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
    (ex-data ex)
    (do
       ;; Highly suspicious solution. What kind of error happens when above two result
       ;; in nil
      (timbre/error "Error when making OAI call. Error data:" ex)
      {:error ex}))))

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
     (timbre/infof "💬 Calling OAI completion with:")
     (timbre/infof "\t* Params: '%s'" (dissoc params :prompt))
     (timbre/infof "\t* Options: '%s'" (dissoc opts :api-key))
     (try
       (if (chat-model? model)
         (create-chat-completion prompt params opts)
         (create-completion prompt params opts))
       (catch Exception e
         (throw (->error e)))))))

(defn chat-completion
  ([messages props opts]
   (chat-completion (assoc props :messages messages) opts))
  ([{:keys [messages model] :as params} opts]
   (let [params   (if model params (assoc params :model cgpt-35))
         messages (mapv chat/bosquet->chatml messages)]
     (timbre/infof "💬 Calling OAI chat with:")
     (timbre/infof "\tParams: '%s'" (dissoc params :prompt))
     (timbre/infof "\tConfig: '%s'" (dissoc opts :api-key))
     (try
       (timbre/spy
        (-> params
            (assoc :messages messages)
            (api/create-chat-completion opts)
            ->completion))
       (catch Exception e
         (throw (->error e)))))))

(def openai
  "Service name to refernece OpenAI"
  ::open-ai)

(deftype OpenAI
         [opts]
  llm/LLM
  (service-name [_this] openai)
  (generate [_this prompt params]
    (complete prompt params opts))
  (chat     [_this conversation params]
    (chat-completion conversation params opts)))
