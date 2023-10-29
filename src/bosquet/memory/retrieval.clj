(ns bosquet.memory.retrieval
  (:require
   [bosquet.llm.llm :as llm]
   [bosquet.llm.openai :as openai]
   [bosquet.llm.openai-tokens :as oai.tokenizer]
   [taoensso.timbre :as timbre]))

;; Memory types are inspired by
;; https://en.wikipedia.org/wiki/Recall_(memory)

(def recall-free
  "Free recall is a common task in the psychological study of memory. In this task,
  participants study a list of items on each trial, and then are prompted to recall the items
  in any order."
  :memory.recall/free)

(def recall-sequential
  "Serial recall is the ability to recall items or events in the order in which they occurred.
  The ability of humans to store items in memory and recall them is important to the use of language."
  :memory.recall/sequential)

(def recall-cue
  "Cued recall refers to retrieving information from long-term memory using aids or cues."
  :memory.recall/cue)

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

(def content-similarity-threshold
  :memory.retrieval/similarity-threshold)

;; (defn free-recall-handler [storage _params]
;;   (shuffle (.query storage identity)))

;; (defn sequential-recall-handler [storage {limit memory-objects-limit}]
;;   (take-last limit
;;              (.query storage identity)))

(defn take-while-tokens
  [{object-limit memory-objects-limit
    token-limit  memory-tokens-limit
    content-fn   memory-content-fn
    model        llm/model
    llm          llm/service
    :or          {object-limit 100
                  token-limit  4000
                  content-fn   identity}}
   objects]
  (if token-limit
    (loop [[object & objects] (reverse (take-last object-limit objects))
           retrieved-objects  []
           token-count        (memory-object-size (content-fn object) model llm)]
      (if (and object (> token-limit token-count))
        (recur
         objects
         (conj retrieved-objects object)
         (+ token-count (memory-object-size (content-fn (first objects)) model llm)))
        (reverse retrieved-objects)))
    (take-last object-limit objects)))

;; (defn cue-recall-handler [{content-fn memory-content-fn
;;                            :or        {content-fn identity}
;;                            :as        params}
;;                           objects cue]
;;   (let [threshold 0.6]
;;     (take-while-tokens
;;      (filter (fn [item]
;;                (> threshold (nlp/cosine-distance (content-fn item) cue)))
;;              objects)
;;      params)))

;; (def handlers
;;   {sequential-recall sequential-recall-handler
;;    free-recall       free-recall-handler})

;; (defn handler [retriever-name]
;;   (get handlers retriever-name
;;     ;; default is `sequential-retriever`
;;        sequential-recall-handler))
