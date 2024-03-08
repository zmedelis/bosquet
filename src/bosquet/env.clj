(ns bosquet.env
  (:refer-clojure :exclude [val])
  (:require
   [aero.core :as aero]
   [bosquet.llm.wkk :as wkk]
   [bosquet.utils :as u]
   [clojure.java.io :as io]))

(def config-file
  "Config file to override `env.edn` or add new components: LLM providers, memory, tools."
  (io/file (System/getProperty "user.home") ".bosquet/config.edn"))


(def secrets-file
  "API keys and other things not to be shared"
  (io/file (System/getProperty "user.home") ".bosquet/secrets.edn"))


(defmethod aero/reader 'mmerge
  [_opts _tag value]
  (apply merge-with merge value))


(defn- read-edn
  [file]
  (when (.exists file)
    (-> file slurp read-string)))


(def model-providers
  (-> (io/resource "model_alias.edn") slurp read-string))


(def config
  (aero/read-config
   (io/resource "env.edn")
   {:resolver aero/root-resolver}))


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


(defn default-service
  "Get default LLM service as defiened in config.edn"
  []
  (let [{params :model-params}
        (config (:bosquet/default-llm config) config)]
    {wkk/service (-> params :model model-providers)
     wkk/model-params params}))
