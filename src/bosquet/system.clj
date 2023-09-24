(ns bosquet.system
  (:require
   [bosquet.memory.memory :as mem]
   [aero.core :as aero :refer [root-resolver]]
   [bosquet.llm.cohere :as cohere]
   [bosquet.llm.openai :as oai]
   [clojure.java.io :as io]
   [integrant.core :as ig]
   [taoensso.timbre :as timbre])
  (:import
   [bosquet.memory.memory SimpleMemory AtomicStorage Amnesiac]
   [bosquet.llm.cohere Cohere]
   [bosquet.llm.openai OpenAI]))

;;
;; Keys to reference sytem components in option maps
;;
(def llm-config
  "Key to reference LLM service components in props when making `generate` calls."
  :bosquet.llm/llm-config)

(def default-llm
  "Key referencing default LLM service in a `system.edn` system config."
  :llm/default-llm)

(def llm-service
  "Key to reference LLM service name in gen call parameters"
  :bosquet.llm/service)

(def model-parameters
  "Key to reference LLM model parameters in gen call parameters"
  :bosquet.llm/model-parameters)

(def ^:private config
  (aero/read-config
   (io/resource "system.edn")
   {:resolver root-resolver}))

(def ^:private sys-config
  (dissoc config :config default-llm))

;;
;; LLM Services
;;

(defmethod ig/init-key :llm/openai [_ {:keys [api-key impl] :as opts}]
  (when api-key
    (timbre/infof " * OpenAI API service (%s)" (name impl))
    (OpenAI. opts)))

(defmethod ig/init-key :llm/cohere [_ {:keys [api-key] :as opts}]
  (when api-key
    (timbre/info " * Cohere API service")
    (System/setProperty "cohere.api.key" api-key)
    (Cohere. opts)))

;;
;; Memory Components
;;

(defmethod ig/init-key :memory/simple-short-term [_ {:keys [encoder retriever] :as opts}]
  (timbre/infof " * Short term memory with (%s)" opts)
  (SimpleMemory.
   (AtomicStorage.)
   (mem/->enconder encoder)
   (mem/->retriever retriever)))

(def system
  (do
    (timbre/info "Initializing Bosquet resources:")
    (ig/init sys-config)))

;;
;; Convenience functions to get LLM API instances
;;

(defn openai []
  (get system [:llm/openai :provider/openai]))

(defn azure []
  (get system [:llm/openai :provider/azure]))

(defn cohere []
  (get system :llm/cohere))

(defn get-service
  "Get LLM service by Integrant confg key. If there is none
  configured under that key - get the default one specified under
  `:llm/default` key."
  [key]
  (or (get system key)
      (get system (config default-llm))))

(defn get-memory
  [key]
  (get system key (Amnesiac.)))
