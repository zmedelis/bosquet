(ns bosquet.llm.cohere
  (:require [bosquet.llm.wkk :as wkk]
            [bosquet.llm.schema :as schema]
            [cohere.client :as client]))

(defn- props->cohere
  "Convert general LLM model properties to Cohere specific ones."
  [{:keys [n stop] :as props}]
  (merge
   (dissoc  props :n :stop)
   (when n {:num_generations n})
   (when stop {:stop_sequences stop})))

(defn usage->canonical
  [{:keys [input_tokens output_tokens]}]
  {schema/usage-in-count input_tokens
   schema/usage-out-count output_tokens
   schema/usage-total-count (+ output_tokens input_tokens)})

(defn complete
  ([prompt opts]
   (let [{{usage :billed_units} :meta generations :generations}
         (client/generate (props->cohere (assoc opts :prompt prompt)))]
     {wkk/generation-type :completion
      wkk/content         {:completion (-> generations first :text)}
      wkk/usage           (usage->canonical usage)}))
  ([prompt]
   (complete prompt {})))

(comment
  (client/generate (props->cohere
                    {:model          "command"
                     :prompt  "Today is a"
                     :n              1
                     :stop-sequences ["\n"]
                     :temperature    0.2}))
  (complete
   "A party is about to begin."
   {:model "command"
    :n 2
    :stop-sequences ["\n"]
    :temperature 0.2}))
