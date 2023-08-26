(ns bosquet.system
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [integrant.core :as ig])
  (:import [bosquet.llm.cohere Cohere]
           [bosquet.llm.openai OpenAI]))

(defn- load-opts [filename]
  (with-open [r (io/reader filename)]
    (edn/read (java.io.PushbackReader. r))))

(def ^:private opts (load-opts "config.edn"))

(defn- config-val
  "Get configuration value from environment (priority) or config.edn"
  [k]
  (or (System/getenv k) (opts k)))

(def config
  {;; Config for OpenAI LLM provided by OpenAI
   [:llm/openai :provider/openai] {:api-key (config-val "OPENAI_API_KEY")
                                   :impl    :openai}
   ;; Config for OpenAI LLM provided by MS Azure
   [:llm/openai :provider/azure]  {:api-key      (config-val "AZURE_OPENAI_API_KEY")
                                   :api-endpoint (config-val "AZURE_OPENAI_API_ENDPOINT")
                                   :impl         :azure}
   ;; Config for Cohere LLM
   :llm/cohere                    {:api-key (config-val "COHERE_API_KEY")}})

(defmethod ig/init-key :llm/openai [_ opts]
  (OpenAI. opts))

(defmethod ig/init-key :llm/cohere [_ {:keys [api-key] :as opts}]
  (System/setProperty "cohere.api.key" api-key)
  (Cohere. opts))

(def system
  (ig/init config))

;;; Convenience functions to get LLM API instances

(defn openai [] (get system [:llm/openai :provider/openai]))

(defn azure [] (get system [:llm/openai :provider/azure]))

(defn cohere [] (get system :llm/cohere))
