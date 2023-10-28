(ns bosquet.memory.simple-memory
  (:require
   [bosquet.llm.llm :as llm]
   [bosquet.llm.openai :as openai]
   [bosquet.memory.memory :as mem]
   [bosquet.memory.retrieval :as r]))

(def memory-store
  "This type of mem is mainly for dev purposes. Expose the atom for easy debuging."
  (atom []))

(deftype
 SimpleMemory
 [in-memory-memory] mem/Memory

 (forget [_this _params]
   (reset! in-memory-memory []))

 (remember [_this observation _params]
   (doseq [item (if (vector? observation) observation [observation])]
     (swap! in-memory-memory conj item)))

 (free-recall [_this _cueue {object-limit r/memory-objects-limit
                             :or          {object-limit 5}}]
   (->> @in-memory-memory shuffle (take object-limit)))

 (sequential-recall [_this {object-limit r/memory-objects-limit
                            token-limit  r/memory-tokens-limit
                            :as          params}]

      ;; WIP
   (cond->> @in-memory-memory
     object-limit (take-last object-limit)
     token-limit  (r/take-while-tokens
                   (merge {llm/model   "gpt-3.5-turbo"
                           llm/service openai/openai}
                          params))))

 (cue-recall [_this cue params]
   #_(r/cue-recall-handler @in-memory-memory cue
                         (merge
                          {r/memory-content-fn :content
                           llm/model           "gpt-3.5-turbo"
                           llm/service         openai/openai}
                          params)))

 (volume [_this {service        :bosquet.llm/service
                 {model :model} :bosquet.llm/model-parameters}]))
