(ns bosquet.memory.retrieval)

(def free-recall
  "'Free recall' retriever"
  :memory.retrieval/free)

(def sequential-recall
  "'Sequential recall' retriever"
  :memory.retrieval/sequential)

(def memory-objects-limit
  "A limit on how many objects a memory can hold. Note
  that it does not deal with tokens. Thus even a single memory
  object might be over the token limit"
  :memory.retrieval/object-limit)

(defn free-recall-handler [storage _params]
  (shuffle (.query storage identity)))

(defn sequential-recall-handler [storage {limit memory-objects-limit}]
  (.query storage identity))

(def handlers
  {sequential-recall sequential-recall-handler
   free-recall       free-recall-handler})

(defn handler [retriever-name]
  (get handlers retriever-name
    ;; default is `sequential-retriever`
       sequential-recall-handler))
