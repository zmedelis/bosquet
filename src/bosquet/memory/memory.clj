(ns bosquet.memory.memory)

;; https://gentopia.readthedocs.io/en/latest/agent_components.html#long-short-term-memory
;; Memory component is used for one of the following purposes:

;; - Escaping context limitation of LLMs. eg. when you expect a very long
;;   conversation or task solving trajectory, exceeding the max_token limit
;;   of LLMs.

;; - Saving token consumption. eg. when you expect to have lengthy and
;;   unnecessary tool response (like Wikipedia Search) stored in-context.

;; https://arxiv.org/pdf/2304.03442.pdf
;; memory stream, a long-term memory module that records, in natural language,
;; a comprehensive list of the agent’s experiences.
;; A memory retrieval model combines relevance, recency, and importance to
;; surface the records needed to inform the agent’s moment-to-moment behavior.

;; The memory stream maintains a comprehensive record of the agent’s experience.
;; It is a list of memory objects, where each object contains a natural language
;; description, a creation timestamp, and a most recent access timestamp. The most basic element
;; of the memory stream is an observation, which is an event directly
;; perceived by an agent.

(defprotocol Memory
  ;; Encode and store an observation into memory store
  (remember [this observation])
  ;; Recall the memory by cueue
  (recall [this cueue])
  ;; Remove all memory objects matching cueue from memory store
  (forget [this cueue]))

(defprotocol Encoder
  ;; Encode an observation into a memory object
  (encode [this observation]))

(defprotocol Storage
  ;; Store a memory object for later retrieval via recall
  (store [this observation])
  (query [this params])
  ;; What is the size in `tokens` of the memory
  (volume [this tokenizer]))

(defprotocol Retriever
  ;; Recall memory object give a cueue
  (retrieve [this storage cueue]))

(deftype DummyEncoder
         []
  Encoder
  (encode [_this observation] observation))

(deftype AtomicStorage
         [atom]
  Storage
  (store [_this observation] (swap! atom conj observation))
  (query [_this pred] (filter pred @atom))
  (volume [_this tokenizer]))

(deftype ExactRetriever
         []
  Retriever
  (retrieve [_this storage cueue]
    (.query storage #(= cueue %))))

(deftype TestMemory
         [encoder storage retriever]
  Memory
  (remember [_this observation]
    (.store
     storage
     (.encode encoder observation)))
  (recall [_this cueue]
    (.retrieve retriever storage cueue))
  (forget [_this cueue]))

;; Encode:
;; Store: Atom, VectorDB
;; Retrieve: Sequential, Cueue, Query
;;
;; (def x (encode "long doc"))

(comment
  (def mem
    (TestMemory.
     (DummyEncoder.)
     (AtomicStorage. (atom []))
     (ExactRetriever.)))
  (.remember mem "long doc")
  (.recall mem "long doc")
  #__)
