(ns bosquet.llm.oai-shaped-llm
  (:require
   [bosquet.llm.http :as http]
   [bosquet.llm.wkk :as wkk]
   [clojure.set :as set]))

(defn with-default
  "If no model is given in `params` add the default"
  [{:keys [model] :as params} default-model]
  (if (or model (nil? default-model))
    params
    (assoc params :model default-model)))

(defn prep-params
  "Shape `params` into the LLM API service required structure.
  Remove or move `Bosquet` parameters.

  If `params` has no `model` specified `default-model` will be used."
  ([params] (prep-params params nil))
  ([params default-model]
   (-> params
       (with-default default-model)
       (dissoc wkk/model-params)
       (merge (wkk/model-params params)))))

;; ## ChatML

(def role
  :role)

(def content
  :content)

(def system
  "Key to reference `system` role in ChatML format"
  :system)

(def user
  "Key to reference `user` role in ChatML format"
  :user)

(def assistant
  "Key to reference `assistant` role in ChatML format"
  :assistant)

(def ^:private role-mapping
  (let [roles {system    :system
               user      :user
               assistant :assistant}]
    (merge roles (set/map-invert roles))))

(defn chatml->bosquet
  [{r :role c :content}]
  {role (role-mapping (keyword r)) content c})


(defn ->completion
  "Build Bosquet completion data structure from
  the OAI-shaped responses.

  Gets only the first of completion `choices`"
  [{[{:keys [text message]} & _choices]    :choices
    {total_tokens      :total_tokens
     prompt_tokens     :prompt_tokens
     completion_tokens :completion_tokens} :usage}]
  (assoc
   (cond
     message {wkk/generation-type :chat
              wkk/content         (chatml->bosquet message)}
     text    {wkk/generation-type :completion
              wkk/content         text})
   wkk/usage   {:prompt     prompt_tokens
                :completion completion_tokens
                :total      total_tokens}))

(defn completion-fn [{:keys [api-endpoint api-key]}]
  (partial http/post (str api-endpoint "/chat/completions") api-key))


(defn create-completion
  ([service-cfg params content default-model]
   (create-completion service-cfg
                      (-> params
                          (assoc :messages content)
                          (dissoc :prompt))
                      default-model))
  ([service-cfg params]
   (create-completion service-cfg
                      params
                      nil))
  ([service-cfg params default-model]
   (let [lm-call (completion-fn service-cfg)]
     (-> params
         (prep-params default-model)
         lm-call
         ->completion))))
