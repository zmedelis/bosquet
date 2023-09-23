(ns bosquet.memory.memory
  (:require
   [bosquet.llm.chat :as chat]
   [bosquet.llm.generator :as gen]
   [bosquet.llm.openai-tokens :as oai.tokenizer]
   [bosquet.system :as sys]))

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
  ;; Encode and store an observation into memory store
  (remember [this observation])
  ;; Recall the memory by cueue
  (recall [this cueue])
  ;; Remove all memory objects matching cueue from memory store
  (forget [this cueue]))

;; (defprotocol Encoder
;;   ;; Encode an observation into a memory object
;;   (encode [this observation]))

(defprotocol Storage
  ;; Store a memory object for later retrieval via recall
  (store [this observation])
  (query [this params])
  ;; What is the size in `tokens` of the memory
  (volume [this opts]))

(defn free-recall [storage _params]
  (shuffle (.query storage identity)))

(defn sequential-recall [storage _params]
  (.query storage identity))

(defn identity-encoder [observation] observation)

#_(defprotocol Retriever
    "Recall memory object given retrieval `params`. Those parameters will
  specify how the memory is to be interogated by different Retrievers"
    (recall [this params]))

;; (deftype FreeRetriever
;;     []
;;     Retriever
;;     (recall [_this params]
;;       (fn [results] (rand results))))

;; (deftype SequentialRetriever
;;     []
;;     Retriever
;;     (recall [_this params]))

;; (deftype IdentityEncoder
;;     []
;;     Encoder
;;     (encode [_this observation] observation))

(defn- token-count [tokenizer-fn text model]
  (tokenizer-fn text model))

(deftype AtomicStorage
         [atom]
  Storage
  (store [_this observation] (swap! atom conj observation))
  (query [_this pred] (filter pred @atom))
    ;; TODO no passing in opts! Construct Memory with Opts and
    ;; have `volume` calc returned from Memory
  (volume [_this {service                      sys/llm-service
                  {model sys/model-parameters} :model}]
    (let [tokenizer
          (condp = service
            [:llm/openai :provider/azure]  oai.tokenizer/token-count
            [:llm/openai :provider/openai] oai.tokenizer/token-count
            :else                          oai.tokenizer/token-count)]
      (reduce (fn [m txt] (+ m  (token-count tokenizer txt model)))
              0 @atom))))

;; (deftype ExactRetriever
;;     []
;;     (retrieve [_this storage cueue]
;;       (.query storage #(cueue %))))

(deftype SimpleMemory
         [encoder storage retriever]
  Memory
  (remember [_this observation]
    (.store
     storage
     (encoder observation)))
  (recall [_this cueue]
    (retriever storage {})
    #_(.retrieve retriever storage cueue))
  (forget [_this cueue]))

;; Encode: Chunking, Semantic, Metadata
;; Store: Atom, VectorDB
;; Retrieve: Sequential, Cueue, Query
;;

(defmacro with-memory
  "This macro will execute LLM `completions` with the aid of supplied
  `memory`.

  The macro will build a code to execute the following sequence:
  * recall needed memory items from its storage
  * inject that retrieved data into `completion` input
    -  for `chat` generation it will be ChatML messages preappended to the
       beginning of the conversation
    - for `completion` generation those will be added as extra data points to
      the Pathom execution map
  * make a LLM generation call (chat or completion)
  * remember the generated data
  * return generated data"
  [memory & completions]
  (let [[gen-fn messages inputs params] (first completions)]
    `(let [memories# (.recall ~memory identity)
           res#
           (~gen-fn
            (concat memories# ~messages)
            ~inputs ~params)]
       (.remember ~memory res#)
       res#)))

(comment
  #_(def e (IdentityEncoder.))
  (def m (atom []))
  (def s (AtomicStorage. m))
  #_(def r (ExactRetriever.))
  #_(def mem (SimpleMemory. e s r))
  (def mem (SimpleMemory. identity-encoder s sequential-recall))
  (.remember mem "long doc")
  (.recall mem #(= % "long doc"))
  (.recall mem identity)
  (.volume s)

  (def params {chat/conversation
               {:bosquet.llm/service          [:llm/openai :provider/openai]
                :bosquet.llm/model-parameters {:temperature 0
                                               :model       "gpt-3.5-turbo"}}})
  (def inputs {:role "cook" :meal "cake"})
  (.remember mem (chat/speak chat/system "You are a brilliant {{role}}."))
  (macroexpand-1
   (quote (with-memory mem
            (gen/chat
             [(c/speak c/user "What is a good {{meal}}?")
              (c/speak c/assistant "Good {{meal}} is a {{meal}} that is good.")
              (c/speak c/user "Help me to learn the ways of a good {{meal}}.")]
             inputs params))))
  #__)
