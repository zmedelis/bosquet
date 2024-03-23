(ns bosquet.memory.memory
  (:require
   [bosquet.memory.retrieval :as r]
   [bosquet.wkk :as wkk]
   [taoensso.timbre :as timbre]))

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
;;
;; Components of memory retrieval
;; - recency
;; - relevancy
;; - importance
;; - reflection
;;
;; Encode: Chunking, Semantic, Metadata
;; Store: Atom, VectorDB
;; Retrieve: Sequential, Cueue, Query
;;

(defprotocol Memory
  (remember [this observation params])
  (forget [this params])
  (free-recall [this params cue])
  (sequential-recall [this params])
  (cue-recall [this params cue])
  ;; TODO volume calculation should not be a concern of Memory
  ;; It can store whatever it can constrained by storage mechanism
  ;; what can be used by the memory is defined by generation model
  (volume [this opts]))

;; Someone who forgets it all. To be used when memory is not needed (default)
(deftype Amnesiac
         []
  Memory
  (remember [_this _observation _params])
  (forget [_this _params])
  (free-recall [_this _cueue _params])
  (sequential-recall [_this _params])
  (cue-recall [_this _cue _params]))

(defn handle-recall
  "Handle memory retrieval. Dispatch to retrieval method based on `recall-function`.

  In case of unspecified retrieval method or not initialized memory system
  return `context` as memories. `Context` is current conversation message, generation prompt,
  or anything else AI gen workflow is currently using."
  [memory-system recall-function context params]
  (if memory-system
    (condp = recall-function
      r/recall-free       (.free-recall memory-system params)
      r/recall-sequential (.sequential-recall memory-system params)
      r/recall-cue        (.cue-recall memory-system params context)
      (do
        (timbre/warnf "Unknown recall method - '%s'. Using 'context' as memories." recall-function)
        context))
    (do
      (timbre/warnf "Memory system is not specified. Using 'context' as memories.")
      context)))


(defn available-memories
  [{system        wkk/memory-system
    recall-fn     wkk/recall-function
    recall-params wkk/recall-parameters} messages]
  (if type
    (do
      (timbre/infof "🧠 Retrieving memories.")
      (timbre/info "\t* Memory:" type)
      (timbre/info "\t* Recall:" recall-fn)
      (timbre/info "\t* Params:" recall-params)
      (handle-recall system recall-fn messages recall-params))
    (do
      (timbre/info "No memory specified, using available context as memories")
      messages)))
