(ns bosquet.memory.retrieval
  (:require
   [bosquet.llm.openai :as openai]
   [bosquet.llm.openai-tokens :as oai.tokenizer]
   [taoensso.timbre :as timbre]))

(def free-recall
  "'Free recall' retriever"
  :memory.retrieval/free)

(def sequential-recall
  "'Sequential recall' retriever"
  :memory.retrieval/sequential)

(defmulti memory-object-size (fn [_memory-object _model llm] llm))

(defmethod memory-object-size
  openai/openai
  [memory-object model _llm]
  (oai.tokenizer/token-count memory-object model))

(defmethod memory-object-size
  :default
  [memory-object model llm]
  (timbre/warnf "No tokenizer for '%s' - '%s'. Using OpenAI tokenization (FIXME)" llm model)
  (oai.tokenizer/token-count memory-object model))

(def memory-objects-limit
  "A limit on how many objects are to be retrieved from the memory.

  Note that it does not deal with tokens. Thus even a single memory
  object might be over the token limit"
  :memory.retrieval/object-limit)

(defn free-recall-handler [storage _params]
  (shuffle (.query storage identity)))

(defn sequential-recall-handler [storage {limit memory-objects-limit}]
  (take-last limit
             (.query storage identity)))

(def handlers
  {sequential-recall sequential-recall-handler
   free-recall       free-recall-handler})

(defn handler [retriever-name]
  (get handlers retriever-name
    ;; default is `sequential-retriever`
       sequential-recall-handler))
