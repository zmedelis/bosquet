(ns bosquet.memory.encoding)

(defprotocol Encoder
  (encode [_this _text]))

;; Memory encoder that changes nothing. Observations are stored as is.
(deftype AsIsEncoder
         [] Encoder
         (encode
           [_this text] text))
