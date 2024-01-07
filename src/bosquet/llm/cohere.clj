(ns bosquet.llm.cohere
  (:require [bosquet.llm.wkk :as wkk]
            [cohere.client :as client]))

(defn- props->cohere
  "Convert general LLM model properties to Cohere specific ones."
  [{:keys [n stop] :as props}]
  (merge
   (dissoc  props :n :stop)
   (when n {:num_generations n})
   (when stop {:stop_sequences stop})))

(defn ->completion
  [text]
  {wkk/generation-type :completion
   wkk/content         {:completion text}
   ;; TODO
   wkk/usage           {}})

(defn complete
  ([prompt opts]
   (-> (client/generate (props->cohere (assoc opts :prompt prompt)))
       :generations
       first
       :text
       ->completion))
  ([prompt]
   (complete prompt {})))

(comment
  (complete
   "A party is about to begin."
   {:model "command"
    :n 2
    :stop-sequences ["\n"]
    :temperature 0.2}))
