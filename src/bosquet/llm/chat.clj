(ns bosquet.llm.chat
  (:require
   [bosquet.llm.wkk :as wkk]
   [clojure.set :as set]))

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
  the OAI-shaped responses"
  [{choices :choices {prompt_tokens     :prompt_tokens
                      completion_tokens :completion_tokens
                      total_tokens      :total_tokens} :usage}
   generation-type]
  (let [result (-> choices first :message chatml->bosquet)]
    {wkk/generation-type generation-type
     wkk/content         result
     wkk/usage           {:prompt     prompt_tokens
                          :completion completion_tokens
                          :total      total_tokens}}))
