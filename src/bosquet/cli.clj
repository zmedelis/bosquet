(ns bosquet.cli
  (:require
   [me.flowthing.pp :as pp]
   [bosquet.llm.generator :as gen]
   [bosquet.utils :as u]
   [clojure.java.io :as io]
   [clojure.tools.cli :refer [parse-opts]]
   [taoensso.timbre :as timbre])
  (:gen-class))

(def cli-options
  [["-p" "--prompt-file PROMPT-FILE" "File containing either chat, graph, or plain string prompt"
    :validate [#(.exists (io/file %)) "Prompt file is not found."]]
   ["-d" "--data-file DATA-FILE" "File containing context data for the prompts"
    :validate [#(.exists (io/file %)) "Data file is not found."]]
   ["-m" "--model MODEL" "Model name"
    :default :gpt-4
    :parse-fn keyword]
   ["-t" "--temperature TEMP" "Generation temerature"
    :id :temperature
    :default 0
    :parse-fn #(Float/parseFloat %)
    :validate [#(<= 0 % 1) "Temperature value must be between 0.0 and 1.0"]]
   ["-s" "--service SERVICE" "LLM service provider"
    :id :service
    :default :openai
    :parse-fn keyword
    :validate [#{:openai :mistral :cohere :local-oai}
               "LLM not supported. Supporting keys for: opeanai, mistral, cohere, and local-oai"]]])

(def config-file (io/file (System/getProperty "user.home") ".bosquet/config.edn"))
(def secrets-file (io/file (System/getProperty "user.home") ".bosquet/secrets.edn"))

(def ^:private model-default
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

(def ^:private update-config-file
  (partial update-props-file config-file))

(def ^:private update-secrets-file
  (partial update-props-file secrets-file))

(defn- set-key [llm-name]
  (print "Enter key:")
  (flush)
  (when-let [api-key (String. (.readPassword (System/console)))]
    (update-secrets-file [llm-name :api-key] api-key)))

(defn- list-set-keys []
  (doseq [llm (-> secrets-file (read-edn) keys)]
    (println (name llm))))

(defn- config-path
  []
  (println (str config-file)))

(defn- set-default [options]
  (update-config-file [model-default] options))

(defn- call-llm [prompt {:keys [prompt-file data-file]}]
  (timbre/set-min-level! :error)
  (println (u/pp-str
            (if prompt-file
              (gen/generate (-> prompt-file slurp read-string)
                            (when data-file (-> data-file slurp read-string)))
              (gen/generate prompt)))))

(defn- action [options arguments]
  (let [[action arg param & _rest] (map keyword arguments)]
    (condp = action
      :models (condp = arg
                :default (set-default options))
      :keys (condp = arg
              :set  (set-key param)
              :list (list-set-keys)
              :path (config-path)
              (list-set-keys))
      (call-llm (first arguments) options))))

(defn -main [& args]
  (let [{:keys [options arguments errors]} (parse-opts args cli-options)]
    (if errors
      (doseq [err errors]
        (println err))
      (action options arguments))))
