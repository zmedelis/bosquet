(ns examples.function-nodes
  (:require
   [bosquet.llm.generator :as g]))


(def prompt
  {:random-number (g/fun (fn [n] (rand-int n)) ['n])
   :repeat        "Repeat 'X' {{random-number}} times: {{repeater}}"
   :repeater      (g/llm :mistral-small)})


(g/generate prompt {:n 5})


;; sitas geriau nes gali parodyti ir LLM analysis ir function analysis
#_(generate
   {:astronomer ["As a brilliant astronomer, list distances between planets and the Sun"
                 "in the Solar System. Provide the answer in JSON map where the key is the"
                 "planet name and the value is the string distance in millions of kilometers."
                 "Generate only JSON omit any other prose and explanations."
                 "{{distances}}"
                 "Based on the JSON distances data"
                 "provide me with​ a) average distance b) max distance c) min distance"
                 "{{analysis}}"]
    :distances  (llm wkk/openai
                     wkk/output-format :json
                     wkk/model-params {:max-tokens 300 :model :gpt-4})
    :analysis   (llm wkk/mistral
                     wkk/model-params {:model :mistral-small})})
