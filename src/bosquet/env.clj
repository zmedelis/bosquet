(ns bosquet.env
  (:refer-clojure :exclude [val])
  (:require
   [aero.core :as aero]
   [bosquet.llm.wkk :as wkk]
   [bosquet.utils :as u]
   [clojure.java.io :as io]
   [taoensso.timbre :as log]))


(defn exists? [file] (.exists file))


(defn bosquet-cfg-file
  "Get Bosquet config file (secrets.edn or config.edn). First check project root
  then go to ~/.bosquet"
  [cfg-file-name]
  (let [local-file    (io/file (str "./" cfg-file-name))
        home-dir-file (io/file (System/getProperty "user.home")
                               (str ".bosquet/" cfg-file-name))]
    (cond
      (exists? local-file)    local-file
      (exists? home-dir-file) home-dir-file
      :else                   (do
                                (spit local-file "{}")
                                local-file))))


(def config-file
  "Config file to override `env.edn` or add new components: LLM providers, memory, tools."
  (bosquet-cfg-file "config.edn"))


(def secrets-file
  "API keys and other things not to be shared"
  (bosquet-cfg-file "secrets.edn"))


(defmethod aero/reader 'mmerge
  [_opts _tag value]
  (apply merge-with merge value))


(defmethod aero/reader 'include-config
  [_opts _tag value]
  (let [cfg-file (bosquet-cfg-file value)
        config   (if cfg-file (u/read-edn-file cfg-file) {})]
    (when (empty? config)
      (log/infof "No '%s' configuration, using defaults if applicable."
                 value))
    config))


(defn- read-edn
  [file]
  (if (and file (.exists file))
    (-> file slurp read-string)
    {}))


(def config
  (aero/read-config (io/resource "env.edn")))


(def model-providers
  "A list of model names supported by this service. It is an
   optional data point that allows a shortcut when defining LLM
   calls with (generator/llm) function. Instead of
   `(llm :openai :model-params {:model :gpt-3.5})`
   a shorthand of `(llm :gpt-3.5)` will work"
  (reduce-kv (fn [m k {:keys [model-names chat-fn complete-fn]}]
               ;; The IF is a product of not separating llm definitions
               ;; from other stuff like QDRANT in edn.env
               ;; It does not hurt to have qdrant def being processed here
               ;; but it would add junk
               (if (or chat-fn complete-fn)
                 (reduce (fn [model-mapping model-name]
                           (assoc model-mapping model-name k))
                         m model-names)
                 m))
             {}
             config))


(defn val
  "Get configuration at path"
  [& path]
  (get-in config path))


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
  "Get default LLM service as defiened in config.edn.
  In case default is not defined, fall back to OpenAI"
  []
  (let [default-llm (:default-llm config)
        default-llm (if default-llm
                      default-llm
                      (-> config :openai :model-params))]
    {wkk/service      (-> default-llm :model model-providers)
     wkk/model-params (dissoc default-llm
                              :service :default-for-models
                              :api-key :api-endpoint :impl
                              wkk/service wkk/chat-fn wkk/complete-fn wkk/embed-fn)}))
