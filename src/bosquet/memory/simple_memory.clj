(ns bosquet.memory.simple-memory
  (:require
   [bosquet.memory.memory :as mem]
   [bosquet.memory.retrieval :as r]))

(deftype SimpleMemory
         [in-memory-memory encoder]
  mem/Memory

  (remember [_this observation]
    (doseq [item (if (vector? observation) observation [observation])]
      (swap! in-memory-memory conj item)))

  (free-recall [_this _cueue {limit r/memory-objects-limit}]
    (if limit
      (take-last limit @in-memory-memory)
      @in-memory-memory))

  (sequential-recall [this params]
    (.free-recall this nil params))

  (cue-recall [this _cue _params] (.free-recall this nil nil))

  (volume [_this {service :bosquet.llm/service
                  {model :model} :bosquet.llm/model-parameters}]))
