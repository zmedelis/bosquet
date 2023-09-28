(ns bosquet.memory.simple-memory
  (:require
   [bosquet.llm.openai-tokens :as oai.tokenizer]
   [bosquet.memory.memory :as mem]))

(def in-memory-memory (atom []))

(deftype SimpleMemory
    [encoder]
    mem/Memory

    (remember [_this observation]
      (doseq [item (if (vector? observation) observation [observation])]
        (swap! in-memory-memory conj item)))

    (free-recall [_this _cueue _params]
      @in-memory-memory)

    (sequential-recall [this _params] (.free-recall this nil nil))

    (cue-recall [this _cue _params] (.free-recall this nil nil))

    (volume [_this {service :bosquet.llm/service
                    {model :model} :bosquet.llm/model-parameters}]
      (let [tokenizer
            (condp = service
              [:llm/openai :provider/azure]  oai.tokenizer/token-count
              [:llm/openai :provider/openai] oai.tokenizer/token-count
              :else                          oai.tokenizer/token-count)]
        (reduce (fn [m {txt :content}] (+ m  (tokenizer txt model)))
          0 @in-memory-memory))))
