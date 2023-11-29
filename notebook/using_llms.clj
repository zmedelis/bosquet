(ns using-llms
  (:require
   [bosquet.llm.generator :as g]
   [bosquet.wkk :as wkk]))

;; ## Using LLMs

(def prompt
  {:role            "As a brilliant {{you-are}} answer the following question."
   :question        "What is the distance between Io and Europa?"
   :question-answer "{{role}} Question: {{question}}  Answer: {% gen var-name=answer %}"
   :self-eval       "{{answer}} Is this a correct answer? {% gen var-name=test %}"})

(def params
  {:max-tokens  100
   :temperature 0.0})

(def data
  {:you-are  "astronomer"
   :question "What is the distance from Moon to Io?"})

;; ### OpenAI

(g/generate
 prompt
 data
 {:answer {wkk/service          :llm/openai
           wkk/model-parameters params}
  :test   {wkk/service :llm/openai}})


;; ### Local LLMs via LM Studio



;; ### Cohere
