(ns examples.function-nodes
  (:require
   [bosquet.llm.generator :as g]
   [bosquet.llm.wkk :as wkk]))

;; Bosquet prompts are defined in a map, where relationship between
;; prompt components are resolved when constructing the output.
;;
;; An entry in a map with `:llm/service` value defines an LLM call that will
;; recieve a context from already resolved text in the prompt map.

{:repeat   "Repeat 'X' {{number}} times: {{repeater}}"
 :repeater #:llm{:service      :mistral
                 :model-params {:model :mistral-small}}}

;; The concept of calling an LLM is further abstracted into defining any
;; function call. This allows the integration of any data extraction
;; functionality into the prompting flow.

{:rnumber  #:fun{:impl (g/fun (fn [n] (rand-int n)) ['n])
                 :args ['n]}
 :repeat   "Repeat 'X' {{number}} times: {{repeater}}"
 :repeater #:llm{:service      :mistral
                 :model-params {:model :mistral-small}}}

;; The function call definition requires two values:
;; - a function itself under `fun` key
;; - arguments to that function under `args` key,
;;
;; `generator` namespace has helper functions to define those nodes

(g/generate
 {:repeat   "Repeat 'X' {{number}} times: {{repeater}}"
  :number   (g/fun (fn [n] (rand-int n)) ['n])
  :repeater (g/llm :mistral-small)}
 {:n 5})

;; An example is where function and llm invocation nodes use the data they produce.

(g/generate
 {:format     "EDN"
  :astronomer ["As a brilliant astronomer, list distances between planets and the Sun"
               "in the Solar System. Provide the answer in {{format}} map where the key is the"
               "planet name and the value is the number of the distance in millions of kilometers."
               "Generate only {{format}} omit any other prose and explanations."
               "{{distances}}"
               "Based on the distances data we know that the average min and max distances are:"
               "{{analysis}}"
               ]
  :distances  (g/llm :gpt-4
                     wkk/cache         true
                     wkk/output-format :edn
                     wkk/model-params {:max-tokens 300 :model :gpt-4})
  :analysis   (g/fun (fn [d]
                       [(-> d vals min) (-> d vals max)])
                     ['distances])
  #_          (llm wkk/mistral
                   wkk/model-params {:model :mistral-small})})
