(ns bosquet.llm.openai
  (:require
   [bosquet.llm.chat :as chat]
   [bosquet.llm.wkk :as wkk]
   [clojure.string :as string]
   [jsonista.core :as j]
   [taoensso.timbre :as timbre]
   [wkok.openai-clojure.api :as api]))

(defn chat-model?
  "Check if `model` (name provided as string) is a chat model"
  [model]
  (string/starts-with? (name model) "gpt-"))

(defn- ->completion*
  [generation-type content {:keys [prompt_tokens completion_tokens total_tokens]}]
  {wkk/generation-type generation-type
   wkk/content         content
   wkk/usage           {:prompt     prompt_tokens
                        :completion completion_tokens
                        :total      total_tokens}})

(defmulti ->completion (fn [{object :object}] object))

(defmethod ->completion "chat.completion"
  [{:keys [choices usage]}]
  (->completion* :chat
                 (-> choices first :message chat/chatml->bosquet)
                 usage))

(defmethod ->completion "text_completion"
  [{:keys [choices usage]}]
  (->completion* :completion
                 (-> choices first :text)
                 usage))

(defn create-chat-completion
  "Completion using Chat GPT model. This one is loosing the conversation
  aspect of the API. It will construct basic `system` for the
  conversation and then use `prompt` as the `user` in the chat "
  [prompt params opts]
  (let [result (->completion
                (api/create-chat-completion
                 (assoc params
                        :messages [{:role :user :content prompt}])
                 opts))]
    ;; wrangle the resulting data structure into `completion` format
    (-> result
        (assoc-in [wkk/generation-type] :completion)
        (assoc-in [wkk/content :completion] (-> result wkk/content :completion :content)))))

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
      (timbre/error "Error when making OAI call. Error data:" ex)
      {:error ex}))))

(defn complete
  "Complete `prompt` if passed in `model` is cGPT the completion will
  be passed to `complete-chat`"
  ([prompt] (complete prompt nil nil))
  ([prompt params] (complete prompt params nil))
  ([prompt {:keys [model]
            :or   {model :gpt-3.5-turbo}
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
   (let [params   (if model params (assoc params :model :gpt-3.5-turbo))
         messages (mapv chat/bosquet->chatml messages)]
     (timbre/infof "💬 Calling OAI chat with:")
     (timbre/infof "\tParams: '%s'" (dissoc params :prompt))
     (timbre/infof "\tConfig: '%s'" (dissoc opts :api-key))
     (try
       (let [payload (assoc params :messages messages)
             result (api/create-chat-completion payload opts)]
         (->completion result))
       (catch Exception e
         (throw (->error e)))))))
