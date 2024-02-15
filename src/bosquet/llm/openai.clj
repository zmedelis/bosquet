(ns bosquet.llm.openai
  (:require
   [bosquet.env :as env]
   [bosquet.llm.chat :as chat]
   [bosquet.llm.wkk :as wkk]
   [bosquet.utils :as u]
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

(def default-model :gpt-3.5-turbo)

(defn- with-default [{:keys [model] :as params}]
  (if model params (assoc params :model default-model)))

(defn- prep-params
  ;; TODO this is repeated in other LLMs like mistral ns,
  ;; needs to be normalized (more things around chat/complete)
  [params]
  (-> params
      with-default
      (dissoc wkk/model-params)
      (merge (wkk/model-params params))))

(defn chat
  "Run 'chat' type completion. Pass in `messages` in ChatML format."
  ([params] (chat (wkk/openai env/config) params))
  ([service-cfg params]
   (u/log-call service-cfg params "OAI chat")
   (try
     (->completion (api/create-chat-completion (prep-params params) service-cfg))
     (catch Exception e
       (throw (->error e))))))

(defn complete
  "Run 'completion' type generation.
  `params` needs to have `prompt` key.

  *Deprecated* by OAI?"
  ([params] (complete (wkk/openai env/config) params))
  ([service-cfg params]
   (u/log-call service-cfg params "OAI completion")
   (->completion (api/create-completion (prep-params params) service-cfg))))

(comment
  (chat
   {:messages [{:role :user :content "2/2="}]})
  (complete
   {:prompt "2+2="
    wkk/model-params {:model :davinci-002}})
  #__)
