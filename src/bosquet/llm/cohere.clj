(ns bosquet.llm.cohere
  (:require
   [bosquet.llm.llm :as llm]
   [cohere.client :as client]))

;; TODO make sure that the params are in sync with OpenAI options, implement
;; protocol and param normalization
;;
;; https://docs.cohere.ai/api-reference/completions
#_{:keys [max_tokens num_generations truncate stream model p k presence_penalty frequency_penalty temperature prompt preset end_sequences stop_sequences return_likelihoods logit_bias]
   :or {max_tokens 300
        num_generations 1
        truncate "END"
        model "command"
        p 0
        k 0
        presence_penalty 0
        frequency_penalty 0
        temperature 0.75
        stream false
        return_likelihoods "NONE"}}

(defn complete
  ([prompt opts]
   (-> (client/generate (assoc opts :prompt prompt))
       :generations
       first
       :text))
  ([prompt]
   (complete prompt {})))

(deftype Cohere
         [config]
  llm/LLM
  (generate [_this prompt props]
    (complete prompt (merge config props)))
  (chat     [_this _system _conversation _props]))
