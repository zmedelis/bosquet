(ns bosquet.memory.simple-memory
  (:require
   [bosquet.llm.wkk :as wkk]
   [bosquet.memory.retrieval :as r]
   [bosquet.nlp.similarity :as nlp]))

(def memory-store
  "This type of mem is mainly for dev purposes. Expose the atom for easy debuging."
  (atom []))

(defn forget
  "Clear memory contents"
  []
  (reset! memory-store []))

(defn- retrieve-in-sequnce
  "WIP. Candidate for `retrieval` ns to be reused accross memory systems"
  [{object-limit r/memory-objects-limit
    token-limit  r/memory-tokens-limit
    :as          params} memories]
  (cond->> memories
    object-limit (take-last object-limit)
    token-limit  (r/take-while-tokens
                  (merge {wkk/model   :gpt-3.5-turbo
                          wkk/service wkk/openai}
                         params))))

(defn ->cue-memory
  []
  (fn [{mem-content-fn r/memory-content
        threshold      r/content-similarity-threshold
        :or            {threshold      1
                        mem-content-fn identity}
        :as            params}
       cue]
    (retrieve-in-sequnce
     params
     (filter #(> threshold (nlp/cosine-distance cue (mem-content-fn %)))
             @memory-store))))

(defn ->remember
  []
  (fn [_opts observation]
    (doseq [item (if (sequential? observation) observation [observation])]
      (swap! memory-store conj item))))
