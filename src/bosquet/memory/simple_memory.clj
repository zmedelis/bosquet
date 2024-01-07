(ns bosquet.memory.simple-memory
  (:require
   [bosquet.llm.wkk :as wkk]
   [bosquet.memory.memory :as mem]
   [bosquet.memory.retrieval :as r]
   [bosquet.nlp.similarity :as nlp]))

(def memory-store
  "This type of mem is mainly for dev purposes. Expose the atom for easy debuging."
  (atom []))

(defn- retrieve-in-sequnce
  "WIP. Candidate for `retrieval` ns to be reused accross memory systems"
  [{object-limit r/memory-objects-limit
    token-limit  r/memory-tokens-limit
    :as          params} memories]
  (cond->> memories
    object-limit (take-last object-limit)
    token-limit  (r/take-while-tokens
                  (merge {wkk/model   "gpt-3.5-turbo"
                          wkk/service wkk/openai}
                         params))))

(deftype
 SimpleMemory
 [in-memory-memory] mem/Memory

 (forget [_this _params]
   (reset! in-memory-memory []))

 (remember [_this observation _params]
   (doseq [item (if (vector? observation) observation [observation])]
     (swap! in-memory-memory conj item)))

 (free-recall [_this {object-limit r/memory-objects-limit :or {object-limit 5}} _cue]
   (->> @in-memory-memory shuffle (take object-limit)))

 (sequential-recall [_this params]
   (retrieve-in-sequnce params @in-memory-memory))

 (cue-recall [_this {mem-content-fn r/memory-content-fn
                     threshold      r/content-similarity-threshold
                     :as            params}
              cue]
   (retrieve-in-sequnce
    params
    (filter #(> threshold (nlp/cosine-distance cue (mem-content-fn %)))
            @in-memory-memory)))

 (volume [_this _params]))
