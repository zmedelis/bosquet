(ns bosquet.memory.retrieval)

(def free-recall
  "'Free recall' retriever"
  :memory.retrieval/free)

(def sequential-recall
  "'Sequential recall' retriever"
  :memory.retrieval/equential)

(defn free-recall-handler [storage _params]
  (shuffle (.query storage identity)))

(defn sequential-recall-handler [storage _params]
  (.query storage identity))

(def handlers
  {sequential-recall sequential-recall-handler
   free-recall       free-recall-handler})

(defn handler [retriever-name]
  (get handlers retriever-name
    ;; default is `sequential-retriever`
       sequential-recall-handler))
