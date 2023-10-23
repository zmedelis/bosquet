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
;; also should be in System

(defn ->cache []
  (w/fifo-cache-factory {}))

(def cache (->cache))

(defn evict [prompt model-params]
  (w/evict cache {:prompt prompt
                  :params model-params}))

(defn generate-with-cache [cache? generator prompt model-parameters]
  (if cache?
    (w/lookup-or-miss
     cache
     {:prompt prompt
      :params model-parameters}
     (fn [_item] (generator prompt model-parameters)))
    (generator prompt model-parameters)))

(defn complete
  [prompt {gen-var wkk/gen-var-name :as opts}]
  (let [{service       wkk/service
         params        wkk/model-parameters
         cache         wkk/cache
         output-format wkk/output-format}
        (get-in opts [wkk/llm-config (or gen-var wkk/default-gen-var-name)])

        service   (sys/get-service service)
        generator (fn [prompt params] (.generate service prompt params))

        {{completion :completion} llm/content :as generation}
        (generate-with-cache cache generator prompt params)]

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
    (.remember memory messages nil)
    (.remember memory (-> completion llm/content :completion) nil)
    completion))
