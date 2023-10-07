(ns bosquet.memory.simple-memory
  (:require
   [bosquet.llm.llm :as llm]
   [bosquet.llm.openai :as openai]
   [bosquet.memory.memory :as mem]
   [bosquet.memory.retrieval :as r]))

(deftype SimpleMemory
         [in-memory-memory encoder]
  mem/Memory

  (forget [_this]
    (reset! in-memory-memory []))

  (remember [_this observation]
    (doseq [item (if (vector? observation) observation [observation])]
      (swap! in-memory-memory conj item)))

  (free-recall [_this _cueue {object-limit r/memory-objects-limit
                              token-limit  r/memory-tokens-limit
                              :or          {object-limit 5
                                            token-limit  3000}}]
    (->> @in-memory-memory shuffle (take object-limit)))

  (sequential-recall [_this params]
      ;; WIP
    (r/take-while-tokens
     @in-memory-memory
     (assoc params
            r/memory-content-fn :content
            llm/model "gpt-3.5-turbo"
            llm/service openai/openai)))

  (cue-recall [this _cue _params] (.free-recall this nil nil))

  (volume [_this {service        :bosquet.llm/service
                  {model :model} :bosquet.llm/model-parameters}]))
