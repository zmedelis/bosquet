(ns bosquet.complete
  (:require
   [bosquet.llm.chat :as llm.chat]
   [bosquet.llm.llm :as llm]
   [bosquet.system :as system]
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

(defn complete [prompt {gen-key :the-key :as opts}]
  (let [{:bosquet.llm/keys [service model-parameters cache]} (get-in opts [system/llm-config gen-key])
        llm (system/get-service service)]
    (if cache
      (complete-with-cache llm prompt model-parameters)
      (.generate llm prompt model-parameters))))

(defn available-memories
  [_messages opts]
  (let [{:bosquet.memory/keys [type parameters]}
        (get-in opts [llm.chat/conversation])]
    (.sequential-recall (system/get-memory type) parameters)))

(defn chat-completion [messages opts]
  (let [{:bosquet.llm/keys    [service model-parameters]
         :bosquet.memory/keys [type]}
        (get-in opts [llm.chat/conversation])
        llm        (system/get-service service)
        memory     (system/get-memory type)
        memories   (available-memories messages opts)
        completion (.chat llm (concat memories messages) model-parameters)]
    (.remember memory messages)
    (.remember memory (-> completion llm/content :completion))
    completion))
