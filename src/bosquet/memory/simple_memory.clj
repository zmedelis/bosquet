(ns bosquet.memory.simple-memory
  (:require
   [bosquet.llm.openai-tokens :as oai.tokenizer]
   [bosquet.memory.memory :as mem]))

(defn- token-count [tokenizer-fn text model]
  (tokenizer-fn text model))

(def in-memory-memory (atom []))

(deftype AtomicStorage
         []
  mem/Storage
  (store [_this observation]
    (swap! in-memory-memory conj observation))
  (query [_this pred] (filter pred @in-memory-memory))
    ;; TODO no passing in opts! Construct Memory with Opts and
    ;; have `volume` calc returned from Memory
  (volume [_this {service :bosquet.llm/service
                  {model :bosquet.llm/model-parameters} :model}]
    (let [tokenizer
          (condp = service
            [:llm/openai :provider/azure]  oai.tokenizer/token-count
            [:llm/openai :provider/openai] oai.tokenizer/token-count
            :else                          oai.tokenizer/token-count)]
      (reduce (fn [m txt] (+ m  (token-count tokenizer txt model)))
              0 @in-memory-memory))))

(deftype SimpleMemory
         [storage encoder retriever]
  mem/Memory
  (remember [_this observation]
    (if (vector? observation)
      (doseq [item observation]
        (.store storage (encoder item)))
      (.store storage (encoder observation))))
  (recall [_this cueue]
    (retriever storage {}))
  (forget [_this cueue]))
