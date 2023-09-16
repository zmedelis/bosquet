(ns bosquet.llm.chat)

;;
;; Keys to reference sytem components in option maps
;;

(def system
  "Key to reference `system` role in ChatML format"
  :bosquet.chat/system)


(def role
  :bosquet.chat/role)

(def content
  :bosquet.chat/content)

(def user
  "Key to reference `user` role in ChatML format"
  :bosquet.chat/user)

(def assistant
  "Key to reference `assistant` role in ChatML format"
  :bosquet.chat/assistant)

(def conversation
  "Key to reference complete `conversation` - a history"
  :bosquet.chat/conversation)

(def memory
  "Key to reference `memory` of the conversation"
  :bosquet.chat/memory)

(def last-message
  "Key to reference `last-message` in the conversation"
  :bosquet.chat/last-message)

(defn speak
  "Helper function to create a chat message

  ```clojure
  {:bosquet.chat/role    :assistant
   :bosqyet.chat/content \"Hello, I am your assistant\"
  ```"
  [speaker-role speaker-content]
  {role    speaker-role
   content speaker-content})
