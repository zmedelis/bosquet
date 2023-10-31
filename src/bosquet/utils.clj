(ns bosquet.utils
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [hato.client :as hc]
   [jsonista.core :as j]
   [taoensso.timbre :as timbre]
   [me.flowthing.pp :as pp])
  (:import
   [java.util UUID]))

(defn uuid []
  (UUID/randomUUID))

(defn pp-str
  [x]
  (with-out-str (pp/pprint x)))

