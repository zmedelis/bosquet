(ns bosquet.llm.openai
  (:require
   [bosquet.llm.chat :as chat]
   [bosquet.llm.wkk :as wkk]
   [jsonista.core :as j]
   [taoensso.timbre :as timbre]
   [wkok.openai-clojure.api :as api]))

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

(defn chat
  "Run 'chat' type completion. Pass in `messages` in ChatML format."
  [service-cfg {:keys [messages model] :as params}]
  (let [params   (if model params (assoc params :model :gpt-3.5-turbo))
        messages (mapv chat/bosquet->chatml messages)]
    (timbre/infof "💬 Calling OAI chat with:")
    (timbre/infof "\tParams: '%s'" (dissoc params :prompt))
    (timbre/infof "\tConfig: '%s'" (dissoc service-cfg :api-key))
    (try
      (let [payload (assoc params :messages messages)
            result (api/create-chat-completion payload service-cfg)]
        (->completion result))
      (catch Exception e
        (throw (->error e))))))

(defn complete
  "Run 'completion' type generation.
  `params` needs to have `prompt` key."
  [service-cfg
   {:keys [model]
    :or   {model :gpt-3.5-turbo}
    :as   params}]
  (let [params (if (nil? params) {:model model} (assoc params :model model))]
    (timbre/infof "💬 Calling OAI completion with:")
    (timbre/infof "\t* Params: '%s'" (dissoc params :prompt))
    (timbre/infof "\t* Options: '%s'" (dissoc service-cfg :api-key))
    (->completion (api/create-completion params service-cfg))))
