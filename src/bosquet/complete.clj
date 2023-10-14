(ns bosquet.complete
  (:require
   [bosquet.converter :as converter]
   [bosquet.llm.chat :as llm.chat]
   [bosquet.llm.llm :as llm]
   [bosquet.system :as sys]
   [bosquet.wkk :as wkk]
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
  (let [{service       wkk/service
         params        wkk/model-parameters
         cache         wkk/cache
         output-format wkk/output-format}
        (get-in opts [wkk/llm-config (or gen-key :gen)])

        service (sys/get-service service)

        {{completion :completion} llm/content :as generation}
        (if cache
          (complete-with-cache service prompt params)
          (.generate service prompt params))]

    (assoc-in generation [llm/content :completion]
              (converter/coerce completion output-format))))

(defn available-memories
  [_messages opts]
  (let [{:bosquet.memory/keys [type parameters]}
        (get-in opts [llm.chat/conversation])]
    (.sequential-recall (sys/get-memory type) parameters)))

(defn chat-completion [messages opts]
  (let [{service       wkk/service
         params        wkk/model-parameters
         type         :bosquet.memory/type}
        (get-in opts [llm.chat/conversation])
        llm        (sys/get-service service)
        memory     (sys/get-memory type)
        memories   (available-memories messages opts)
        completion (.chat llm (concat memories messages) params)]
    (.remember memory messages)
    (.remember memory (-> completion llm/content :completion))
    completion))
