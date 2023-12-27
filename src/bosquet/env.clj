(ns bosquet.env
  (:refer-clojure :exclude [val])
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]))

(defmethod aero/reader 'mmerge
  [_opts _tag value]
  (apply merge-with merge value))

(def config
  (aero/read-config
   (io/resource "env.edn")
   {:resolver aero/root-resolver}))

(defn val [& key]
  (get-in config key))
