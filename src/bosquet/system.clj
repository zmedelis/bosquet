(ns bosquet.system
  (:require
   [aero.core :as aero :refer [root-resolver]]
   [bosquet.llm.cohere :as cohere]
   [bosquet.llm.openai :as oai]
   [bosquet.memory.encoding :as encoding]
   [bosquet.memory.memory :as mem]
   [bosquet.memory.simple-memory :as simple-memory]
   [bosquet.nlp.embeddings :as embeddings]
   [bosquet.wkk :as wkk]
   [clojure.java.io :as io]
   [integrant.core :as ig]
   [taoensso.timbre :as timbre])
  (:import
   [bosquet.llm.cohere Cohere]
   [bosquet.llm.openai OpenAI]
   [bosquet.nlp.embeddings OpenAIEmbeddings]
   [bosquet.memory.memory Amnesiac]
   [bosquet.memory.simple_memory SimpleMemory]))

(def ^:private config
  (aero/read-config
   (io/resource "system.edn")
   {:resolver root-resolver}))

(def ^:private sys-config
  (dissoc config :config wkk/default-llm))

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

(defmethod ig/init-key :memory/simple-short-term [_ {:keys [encoder] :as opts}]
  (timbre/infof " * Short term memory with (%s)" opts)
  (SimpleMemory.
   (atom [])
   (encoding/handler encoder)))

;;
;; Embedding Services
;;
(defmethod ig/init-key :embedding/openai [_ {:keys [api-key impl] :as opts}]
  (when api-key
    (timbre/infof " * OpenAI Embeddings API service (%s)" (name impl))
    (OpenAIEmbeddings. opts)))

(def system
  (do
    (timbre/info "Initializing Bosquet resources:")
    (ig/init sys-config)))

;;
;; Convenience functions to get LLM API instances
;;

(defn openai []
  (get system [:llm/openai :provider/openai]))

(defn get-service
  "Get LLM service by Integrant confg key. If there is none
  configured under that key - get the default one specified under
  `:llm/default` key."
  [key]
  (or (get system key)
      (get system (config wkk/default-llm))))

(defn get-memory
  [key]
  (get system key (Amnesiac.)))
