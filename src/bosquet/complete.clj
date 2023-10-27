(ns bosquet.complete
  (:require
   [bosquet.converter :as converter]
   [bosquet.llm.chat :as llm.chat]
   [bosquet.llm.llm :as llm]
   [bosquet.memory.memory :as memory]
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
  [messages opts]
  (let [{:bosquet.memory/keys [type parameters recall-function]}
        (wkk/memory-config opts)]
    (if type
      (do
        (timbre/infof "🧠 Retrieving memories.")
        (timbre/info "\t* Memory:" type)
        (timbre/info "\t* Recall:" recall-function)
        (timbre/info "\t* Params:" parameters)
        (memory/handle-recall (sys/get-memory type) recall-function messages parameters))
      (do
        (timbre/info "No memory specified, using available context as memories")
        messages))))

(defn chat-completion [messages
                       {{service      wkk/service
                         model-params wkk/model-parameters} llm.chat/conversation
                        {memory-type wkk/memory-type}       wkk/memory-config
                        :as                                 opts}]
  (let [llm        (sys/get-service service)
        memory     (sys/get-memory memory-type)
        memories   (available-memories messages opts)
        completion (.chat llm (concat memories messages) model-params)]
    (.remember memory messages nil)
    (.remember memory (-> completion llm/content :completion) nil)
    completion))
