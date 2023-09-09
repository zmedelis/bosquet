(ns bosquet.chat)

;;
;; Keys to reference sytem components in option maps
;;

(def system
  "Key to reference `system` role in ChatML format"
  :bosquet.conversation/system)


(def history
  "Key to reference `history` of the conversation"
  :bosquet.conversation/history)


(def memory
  "Key to reference `memory` of the conversation"
  :bosquet.conversation/memory)
