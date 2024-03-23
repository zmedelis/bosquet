(ns bosquet.memory.long-term-memory
  (:gen-class)
  (:require
   [bosquet.llm.wkk :as wkk]
   [bosquet.memory.memory :as mem]
   [clojure.core.async :refer [<! chan go go-loop pipeline onto-chan!]]))

(defn- embed-fn
  [{embed-impl wkk/embed-fn :as service-config}]
  (partial (if (symbol? embed-impl)
             ;; symbol comes from edn configs
             (requiring-resolve embed-impl)
             ;; this is when llm config has fn ref
             embed-impl)
           (dissoc service-config
                   wkk/complete-fn wkk/chat-fn wkk/embed-fn)))


(defn store-embeds
  [storage llm opts items]
  (when (seq items) (.create storage))
  (let [batch-size 100
        batches    (partition-all batch-size
                                  (if (sequential? items) items [items]))
        encode-fn  (embed-fn llm)
        in         (chan)
        out        (chan)]

    (pipeline
     8 out
     (map (fn [batch]
            (map (fn [item]
                   (let [e (encode-fn opts item)]
                     {:embedding (:embedding e)
                      :payload   item}))
                 batch)))
     in)

    (go-loop [embeds (<! out)]
      (.add storage embeds)
      (recur (<! out)))

    (go (onto-chan! in batches))))


(deftype LongTermMemory
    [storage llm]
    mem/Memory

    (forget
      [_this {:keys [collection-name]}]
      (.delete storage collection-name))

    (remember
      [_this observation opts]
      (store-embeds storage llm opts observation))

    (free-recall [_this _cueue _params])


    (sequential-recall [_this _params])


    (cue-recall
      [_this cue {:keys [limit] :or {limit 3} :as opts}]
      (let [encode-fn (embed-fn llm)]
        (.search storage
                 (:embedding (encode-fn opts cue))
                 limit)))

    (volume [_this _opts]))
