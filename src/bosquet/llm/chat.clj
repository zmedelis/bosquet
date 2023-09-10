(ns bosquet.llm.chat)

;;
;; Keys to reference sytem components in option maps
;;

(def system
  "Key to reference `system` role in ChatML format"
  :bosquet.chat/system)

(def conversation
  "Key to reference complete `conversation` - a history"
  :bosquet.chat/conversation)

(def memory
  "Key to reference `memory` of the conversation"
  :bosquet.chat/memory)

(def last-message
  "Key to reference `last-message` in the conversation"
  :bosquet.chat/last-message)
