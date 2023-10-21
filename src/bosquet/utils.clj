(ns bosquet.utils
  (:import [java.util UUID]))

(defn uuid []
  (UUID/randomUUID))
