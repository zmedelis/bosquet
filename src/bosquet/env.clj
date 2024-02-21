(ns bosquet.env
  (:refer-clojure :exclude [val])
  (:require
   [aero.core :as aero]
   [bosquet.utils :as u]
   [clojure.java.io :as io]))

(def config-file (io/file (System/getProperty "user.home") ".bosquet/config.edn"))
(def secrets-file (io/file (System/getProperty "user.home") ".bosquet/secrets.edn"))

(def model-default
  "A key in config edn pointing to a default LLM model props"
  :default-model)

(defn- read-edn
  [file]
  (when (.exists file)
    (-> file slurp read-string)))

(defn- merge-config [cfg conf-path value]
  (merge cfg (assoc-in cfg conf-path value)))

(defn- update-props-file
  [file conf-path value]
  (let [cfg (read-edn file)]
    (try
      (io/make-parents file)
      (spit file
            (-> cfg
                (merge-config conf-path value)
                u/pp-str))
      (catch Exception ex
        (println "Failed to update config file: " (.getMessage ex))
        (println "Restoring config.")
        (spit file cfg)))))

(def update-config-file
  (partial update-props-file config-file))

(def update-secrets-file
  (partial update-props-file secrets-file))

(defn configured-api-keys
  "Get a list of keys set in `secrects.edn`"
  []
  (-> secrets-file (read-edn) keys))

(defn get-defaults
  []
  (-> config-file read-edn :default-model))

(defmethod aero/reader 'mmerge
  [_opts _tag value]
  (apply merge-with merge value))

(def config
  (aero/read-config
   (io/resource "env.edn")
   {:resolver aero/root-resolver}))

(defn val [& key]
  (get-in config key))

(defn default-service
  "Get default LLM service as defiened in config.edn"
  []
  (-> :default-model val :service))

(defn default-model-params
  "Get default LLM model parameters"
  []
  (-> :default-model val (dissoc :service)))
