(ns bosquet.cli
  (:require
   [bosquet.llm.generator :as gen]
   [bosquet.llm.http :as http]
   [bosquet.template.read :as read]
   [bosquet.utils :as u]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.tools.cli :refer [parse-opts]]
   [taoensso.timbre :as timbre])
  (:gen-class))

(def cli-options
  [["-p" "--prompt-file PROMPT-FILE" "File containing either chat, graph, or plain string prompt"
    :validate [#(.exists (io/file %)) "Prompt file is not found."]]
   ["-d" "--data-file DATA-FILE" "File containing context data for the prompts"
    :validate [#(.exists (io/file %)) "Data file is not found."]]
   [nil "--model MODEL" "Model name"
    :parse-fn keyword]
   [nil "--max-tokens NUMBER" "Max tokens to generate"
    :default 300
    :parse-fn #(Integer/parseInt %)
    :validate [pos? "Max tokens must be > 0"]]
   [nil "--temperature TEMP" "Generation temerature"
    :id :temperature
    :default 0
    :parse-fn #(Float/parseFloat %)
    :validate [#(<= 0 % 1) "Temperature value must be between 0.0 and 1.0"]]
   ["-s" "--service SERVICE" "LLM service provider"
    :id :service
    :parse-fn keyword
    :validate [#{:openai :mistral :cohere :local-oai}
               "LLM not supported. Supporting keys for: opeanai, mistral, cohere, and local-oai"]]
   [nil "--proxy" "Use localy configured (localhost:8080) proxy for request/response logging"]
   [nil "--proxy-host HOST" "Hostname for the proxy"]
   [nil "--proxy-port PORT" "Port for the proxy"]

   ["-h" "--help" nil]])

(defn usage
  [summary]
  (->> ["Bosquet CLI tool. Run LLM generations based on suplied prompts and data."
        ""
        "Usage: bllm action [options]"
        ""
        "Options:"
        summary
        ""
        "Management actions:"
        ""
        " keys                      manage LLM service keys"
        "   - set [service name]    set a key for a given serivice"
        "   - list                  list registered services with keys"
        " llms                      manage model parameters"
        "   - set                   set default model parameters"
        "   - defaults              show model defaults"
        "   - list                  show supported LLM services"
        ""
        "Generation actions:"
        ""
        " \"prompt string\"         running with prompt string will trigger generation using default model"
        ""
        "Please refer to https://github.com/zmedelis/bosquet for more information."]
       (string/join \newline)
       println))

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

(defn- read-input [label]
  (printf "%s: " (name label))
  (flush)
  (read-line))

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

(defn show-defaults
  []
  (println (-> config-file read-edn :default-model)))

(defn- set-default [options]
  (update-config-file [model-default] options)
  (println "Defaults:")
  (show-defaults))


(defn- list-llms
  []
  (doseq [llm [:openai :openai-azure :cohere :lmstudio :mistral]]
    (println (name llm))))

(defn- collect-data
  "Ask user to enter data in the console prompt"
  [prompts]
  (loop [m {}
         [slot & slots] (read/data-slots prompts)]
    (if slot
      (recur (assoc m slot (read-input slot)) slots)
      m)))

(defn- call-llm
  "Do the call to LLM and print out the results"
  [prompt {:keys [prompt-file data-file proxy proxy-host proxy-port]}]
  (when proxy (http/use-local-proxy))
  (when (and proxy-host proxy-port) (http/use-local-proxy proxy-host proxy-port))
  (let [prompts   (if prompt prompt (-> prompt-file slurp read-string))
        user-data (if data-file
                    (-> data-file slurp read-string)
                    (collect-data prompts))]
    (if prompt-file
      (doseq [data (if (vector? user-data) user-data [user-data])]
        (println (u/pp-str (gen/generate prompts data))))
      (println (gen/generate prompt user-data)))))

(defn- action [options arguments]
  (let [[action arg param & _rest] (map keyword arguments)]
    (condp = action
      :llms (condp = arg
              :set      (set-default options)
              :defaults (show-defaults)
              :list     (list-llms)
              (list-llms))
      :keys (condp = arg
              :set  (set-key param)
              :list (list-set-keys)
              :path config-path
              (list-set-keys))
      (call-llm (first arguments) options))))

(defn -main [& args]
  (timbre/set-min-level! :error)
  (let [{:keys [options arguments errors summary] :as x} (parse-opts args cli-options)]
    (cond
      (seq errors)       (doseq [err errors] (println err))
      (empty? arguments) (usage summary)
      :else              (action options arguments))))
