(ns bosquet.llm.oai-shaped-llm
  (:require
   [bosquet.llm.http :as http]
   [bosquet.llm.wkk :as wkk]
   [bosquet.utils :as u]
   [clojure.set :as set]))


(defn prep-params
  "Shape `params` into the LLM API service required structure.
  Remove or move `Bosquet` parameters.

  If `params` has no `model` specified model in `default-parms` will be used."
  ([params] (prep-params params nil))
  ([params defaults]
   (-> params
       (u/mergex defaults params)
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
  "Make a call to OAI API shaped service.

  - `service-cfg` will contain props needed to make call: endpoint, model defaults, etc
  - `params` is the main payload of the call containing model params, and prompt in `messages`
  - `content` is intended for `complete` workflow where we do not have chat `messages` in `params`"
  ([service-cfg params content]
   (create-completion service-cfg
                      (-> params
                          (assoc :messages content)
                          (dissoc :prompt))))
  ([{default-params :model-params :as service-cfg} params]
   (let [lm-call (completion-fn service-cfg)]
     (-> params
         (prep-params default-params)
         lm-call
         ->completion))))


(defn chat
  [service-cfg params]
  (create-completion service-cfg params))


(defn complete
  [service-cfg {prompt :prompt :as params}]
  (create-completion service-cfg params
                     [{:role :user :content prompt}]))
