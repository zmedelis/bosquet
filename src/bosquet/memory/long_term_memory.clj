(ns bosquet.memory.long-term-memory
  (:gen-class)
  (:require
   [bosquet.llm.wkk :as wkk]
   [bosquet.memory.retrieval :as r]
   [clojure.core.async :refer [<! chan go go-loop onto-chan! pipeline]]))

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
  "Generate embeddings via the `llm` provider, and save them to `storage`"
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
                   {:embedding (:embedding (encode-fn opts item))
                    :payload   item})
                 batch)))
     in)

    (go-loop [embeds (<! out)]
      (.add storage embeds)
      (recur (<! out)))

    (go (onto-chan! in batches))))

(defn- retrieve-in-sequnce
  "WIP. Candidate for `retrieval` ns to be reused accross memory systems"
  [{object-limit r/memory-objects-limit
    token-limit  r/memory-tokens-limit
    :as          params} memories]
  (cond->> memories
    object-limit (take-last object-limit)
    token-limit  (r/take-while-tokens
                  (merge {wkk/model   :gpt-3.5-turbo
                          wkk/service wkk/openai}
                         params))))


(defn ->cue-memory
  [storage llm]
  (fn [{limit r/memory-objects-limit :or {limit 3} :as opts} cue]
    (let [encode-fn (embed-fn llm)]
      (retrieve-in-sequnce
       opts
       (map :payload (.search storage
                              (:embedding (encode-fn opts cue))
                              limit))))))

(defn ->remember
  [storage llm]
  (fn [opts observation]
    (store-embeds storage llm opts observation)))


#_(deftype LongTermMemory
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
        (retrieve-in-sequnce
         opts
         (.search storage
                  (:embedding (encode-fn opts cue))
                  limit))))

    (volume [_this _opts]))
