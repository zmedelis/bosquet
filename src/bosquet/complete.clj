(ns bosquet.complete
  (:require
   [bosquet.converter :as converter]
   [bosquet.llm.chat :as llm.chat]
   [bosquet.llm.llm :as llm]
   [bosquet.system :as sys]
   [bosquet.wkk :as wkk]
   [clojure.core.cache.wrapped :as w]
   [taoensso.timbre :as timbre]))

;; TODO Cache is basic and experimantal, needs to be improved
;; at last better configuration instead of hardcoding to FIFO
;; also should be in System

(defn ->cache []
  (w/fifo-cache-factory {}))

(def cache (->cache))

(defn evict [prompt model-params]
  (w/evict cache {:prompt prompt
                  :params model-params}))

(defn generate-with-cache
  "Call `generator` function with `prompt` and `model-parameters`.
  If `cache?` is true then use cache store previously generated result.

  Cache gets a hit if `prompt` and `model-parameters` are the same."
  [cache? generator prompt model-parameters]
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

        llm       (sys/get-service service)
        generator (fn [prompt params] (.generate llm prompt params))

        {{completion :completion} llm/content :as generation}
        (generate-with-cache cache generator prompt params)]

    (assoc-in generation [llm/content :completion]
              (converter/coerce completion output-format))))

(defn available-memories
  [messages generation-target opts]
  (let [{:bosquet.memory/keys [type parameters recall-function]}
        (get-in opts generation-target)]
    (if type
      (do
        (timbre/info "Retrieving memories using " type " memory")
        (recall-function (sys/get-memory type) messages parameters))
      (do
        (timbre/info "No memory specified, using available context as memories")
        messages))))

(defn chat-completion [messages opts]
  (let [gen-target [llm.chat/conversation]
        {service wkk/service
         params  wkk/model-parameters
         type    wkk/memory-type}
        (get-in opts gen-target)
        llm        (sys/get-service service)
        memory     (sys/get-memory type)
        memories   (available-memories messages gen-target opts)
        completion (.chat llm (concat memories messages) params)]
    (.remember memory messages nil)
    (.remember memory (-> completion llm/content :completion) nil)
    completion))
