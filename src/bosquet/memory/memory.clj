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
;;
;; Components of memory retrieval
;; - recency
;; - relevancy
;; - importance
;; - reflection

(defprotocol Memory
  (remember [this observation])
  (free-recall [this cueue params])
  (sequential-recall [this params])
  (cue-recall [this cue params])
  ;; TODO volume calculation should not be a concern of Memory
  ;; It can store whatever it can constrained by storage mechanism
  ;; what can be used by the memory is defined by generation model
  (volume [this opts]))

;; Someone who forgets it all. To be used when memory is not needed (default)
(deftype Amnesiac
         []
  Memory
  (remember [this observation])
  (free-recall [this cueue params])
  (sequential-recall [this params])
  (cue-recall [this cue params]))

;; Encode: Chunking, Semantic, Metadata
;; Store: Atom, VectorDB
;; Retrieve: Sequential, Cueue, Query
;;

(comment
  (require '[bosquet.llm.generator :as gen])
  (require '[bosquet.llm.chat :as chat])

  (def params {chat/conversation
               {:bosquet.memory/type          :memory/simple-short-term
                :bosquet.llm/service          [:llm/openai :provider/openai]
                :bosquet.llm/model-parameters {:temperature 0
                                               :model       "gpt-3.5-turbo"}}})
  (def inputs {:role "cook" :meal "cake"})

  (gen/chat
   [(chat/speak chat/system "You are a brilliant {{role}}.")
    (chat/speak chat/user "What is a good {{meal}}?")
    (chat/speak chat/assistant "Good {{meal}} is a {{meal}} that is good.")
    (chat/speak chat/user "Help me to learn the ways of a good {{meal}}.")]
   inputs params)

  (gen/chat
   [(chat/speak chat/user "What would be the name of this recipe?")]
   inputs params)

  (gen/generate
   {:role            "As a brilliant {{you-are}} answer the following question."
    :question        "What is the distance between Io and Europa?"
    :question-answer "Question: {{question}}  Answer: {% gen var-name=answer %}"
    :self-eval       "{{answer}} Is this a correct answer? {% gen var-name=test %}"}
   {:you-are  "astronomer"
    :question "What is the distance from Moon to Io?"}

   {:question-answer {:bosquet.llm/service          [:llm/openai :provider/openai]
                      :bosquet.llm/model-parameters {:temperature 0.4
                                                     :model "gpt-4"}
                       ;; ?
                      :bosquet.memory/type          :bosquet.memory/short-term}
    :self-eval       {:bosquet.llm/service          [:llm/openai :provider/openai]
                      :bosquet.llm/model-parameters {:temperature 0}}}))
