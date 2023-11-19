(ns bosquet.system
  (:require
   [aero.core :as aero]
   [bosquet.llm.cohere :as cohere]
   [bosquet.llm.openai :as oai]
   [bosquet.memory.encoding :as encoding]
   [bosquet.memory.memory :as mem]
   [bosquet.nlp.embeddings :as embeddings]
   [bosquet.memory.simple-memory :as simple-memory]
   [bosquet.memory.long-term-memory :as long-term-memory]
   [bosquet.db.qdrant :as qdrant]
   [bosquet.wkk :as wkk]
   [clojure.java.io :as io]
   [integrant.core :as ig]
   [taoensso.timbre :as timbre])
  (:import
   [bosquet.db.qdrant Qdrant]
   [bosquet.llm.cohere Cohere]
   [bosquet.llm.openai OpenAI]
   [bosquet.memory.memory Amnesiac]
   [bosquet.memory.simple_memory SimpleMemory]
   [bosquet.memory.long_term_memory LongTermMemory]
   [bosquet.nlp.embeddings OpenAIEmbeddings]
   [java.io StringReader]))

(def ^:private config-keys
  "Keys that are to be found in the `config.edn` file."
  [:azure-openai-api-key
   :azure-openai-api-endpoint
   :openai-api-key
   :openai-api-endpoint
   :openai-api-embeddings-endpoint
   :cohere-api-key
   :qdrant-host
   :qdrant-port
   :qdrant-vectors-on-disk
   :qdrant-vectors-size
   :qdrant-vectors-distance])

(defn aero-resolver-with-missing-keys
  "Aero #ref will complain if config is not created and #include fails to add
  keys to the config. This resolver will return nil valued map for missing keys
  when `config.edn` is not created by the user.
"
  [_source include]
  (if (.exists (io/file include))
    include
    (StringReader.
      (pr-str
        ;; config map with nil values for missing keys
        (zipmap config-keys (repeat nil))))))

(def ^:private config
  (aero/read-config
    (io/resource "system.edn")
    {:resolver aero-resolver-with-missing-keys}))

(def sys-config
  (dissoc config :config wkk/default-llm))

;;
;; LLM Services
;;

(defmethod ig/init-key :llm/openai [_ {:keys [api-key impl] :as opts}]
  (when api-key
    (timbre/infof "\t* OpenAI API service (%s)" (name impl))
    (OpenAI. opts)))

(defmethod ig/init-key :llm/cohere [_ {:keys [api-key] :as opts}]
  (when api-key
    (timbre/info "\t* Cohere API service")
    (System/setProperty "cohere.api.key" api-key)
    (Cohere. opts)))

;;
;; Embedding Services
;;

(defmethod ig/init-key :embedding/openai [_ {:keys [api-key impl] :as opts}]
  (when api-key
    (timbre/infof "\t* OpenAI Embeddings API service (%s)" (name impl))
    (OpenAIEmbeddings. opts)))

;;
;; DB
;;
(defmethod ig/init-key :db/qdrant [_ {:keys [host] :as opts}]
  (timbre/infof "\t* Qdrant vector DB on '%s'" host)
  (Qdrant. opts))

;;
;; Memory Components
;;

(defmethod ig/init-key :memory/simple-short-term [_ _opts]
  (timbre/infof "\t* Short term memory")
  (SimpleMemory. simple-memory/memory-store))

(declare system)

;; TODO fix integrant/aero conflicting edn processing and
;; get storage plus encoder from edn
(defmethod ig/init-key :memory/long-term-embeddings [_ _opts #_{:keys [storage encoder] :as opts}]
  (let [storage (get system :db/qdrant)
        encoder (get system :embedding/openai)]
    (timbre/infof "\t* Long term memory with (%s; %s)" storage encoder)
    (LongTermMemory. storage encoder)))

;;
;; Convenience functions to get LLM API instances
;;

(def system
  (do
    (timbre/info "🏗️ Initializing Bosquet resources:")
    (ig/init sys-config)))

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
  (if (contains? system key)
    (get system key)
    (do
      (timbre/warnf "No memory service configured under '%s' key. Using 'Amnesiac' memory." key)
      (Amnesiac.))))
