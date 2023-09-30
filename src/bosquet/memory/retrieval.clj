(ns bosquet.memory.retrieval
  (:require
   [bosquet.llm.llm :as llm]
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

(def memory-tokens-limit
  "A limit on how many tokens are to be retrieved from the memory across
  different memory objects. "
  :memory.retrieval/token-limit)

(def memory-content-fn
  :memory.retrieval/content-fn)

(defn free-recall-handler [storage _params]
  (shuffle (.query storage identity)))

(defn sequential-recall-handler [storage {limit memory-objects-limit}]
  (take-last limit
             (.query storage identity)))

(defn take-while-tokens
  [objects {object-limit memory-objects-limit
            token-limit  memory-tokens-limit
            content-fn   memory-content-fn
            model        llm/model
            llm          llm/service
            :or          {token-limit  3000
                          object-limit 5
                          content-fn   identity}}]
  (if token-limit
    (loop [[object & objects] (reverse (take-last object-limit objects))
           retrieved-objects  []
           token-count        (memory-object-size (content-fn object) model llm)]
      (if (and object (> token-limit token-count))
        (recur
         objects (conj retrieved-objects object)
         (+ token-count (memory-object-size (content-fn object) model llm)))
        (reverse retrieved-objects)))
    (take-last object-limit objects)))

(def handlers
  {sequential-recall sequential-recall-handler
   free-recall       free-recall-handler})

(defn handler [retriever-name]
  (get handlers retriever-name
    ;; default is `sequential-retriever`
       sequential-recall-handler))
