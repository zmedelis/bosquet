(ns bosquet.llm.chat
  (:require
   [clojure.set :as set]
   [malli.generator :as mg]))

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

(def roles [:enum system user assistant])

(def chat-ml
  "Schema defining ChatML response format"
  [:map
   [:role roles]
   [:content string?]])

(def ^:private role-mapping
  (let [roles {system    :system
               user      :user
               assistant :assistant}]
    (merge roles (set/map-invert roles))))

(defn bosquet->chatml
  [{r role c content}]
  {:role (role-mapping r) :content c})

(defn chatml->bosquet
  [{r :role c :content}]
  {role (role-mapping (keyword r)) content c})

(defn speak
  "Helper function to create a chat message

  ```clojure
  {:role    :assistant
   :content \"Hello, I am your assistant\"
  ```"
  {:malli/schema
   [:function
    [:=> [:cat roles :string] chat-ml]]
   :malli/gen mg/generate}
  [speaker-role speaker-content]
  {role    speaker-role
   content speaker-content})

(defn converse
  "Helper function to create onversation sequence. Pass in conversation params:

  ```
  (converse chat/system \"You are a brilliant cook.\"
            chat/user   \"What is a good cookie?\")
  ```

  and this will create a message seq ready to pass to gen service."
  [& utterances]
  (mapv (fn [[role message]] (speak role message))
        (partition 2 utterances)))

(def conversation
  "Key to reference complete `conversation` - a history"
  :bosquet.chat/conversation)
