(ns bosquet.complete
  (:require
   [bosquet.converter :as converter]
   [bosquet.llm.chat :as llm.chat]
   [bosquet.llm.llm :as llm]
   [bosquet.system :as sys]
   [clojure.core.cache.wrapped :as w]))

;; TODO Cache is basic and experimantal, needs to be improved
;; at last better configuration instead of hardcoding to FIFO
;; also should by in System

(defn ->cache []
  (w/fifo-cache-factory {}))

(def cache (->cache))

(defn complete-with-cache [llm prompt model-parameters]
  (w/lookup-or-miss
   cache
   {:prompt prompt
    :params model-parameters}
   (fn [_item]
     (.generate llm prompt model-parameters))))

(defn complete
  [prompt {gen-key :the-key :as opts
           ;; when `gen` tag is not defined, use `gen` as default
           :or     {gen-key :gen}}]
  (let [{:bosquet.llm/keys [service model-parameters cache]
         output-format     sys/generation-format}
        (get-in opts [sys/llm-config (or gen-key :gen)])

        service (sys/get-service service)

        {{completion :completion} llm/content :as generation}
        (if cache
          (complete-with-cache service prompt model-parameters)
          (.generate service prompt model-parameters))]

    (assoc-in generation [llm/content :completion]
              (converter/coerce completion output-format))))

(defn available-memories
  [_messages opts]
  (let [{:bosquet.memory/keys [type parameters]}
        (get-in opts [llm.chat/conversation])]
    (.sequential-recall (sys/get-memory type) parameters)))

(defn chat-completion [messages opts]
  (let [{:bosquet.llm/keys    [service model-parameters]
         :bosquet.memory/keys [type]}
        (get-in opts [llm.chat/conversation])
        llm        (sys/get-service service)
        memory     (sys/get-memory type)
        memories   (available-memories messages opts)
        completion (.chat llm (concat memories messages) model-parameters)]
    (.remember memory messages)
    (.remember memory (-> completion llm/content :completion))
    completion))
