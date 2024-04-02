(ns examples.tree-prompts
  (:require
   [bosquet.llm.generator :refer [llm generate]]
   [bosquet.llm.wkk :as wkk]))

;; TODO

  (def solver (llm :openai wkk/model-params {:model :gpt-4 :max-tokens 50}))

  (def g {:calc       ["Lets solve math problems."
                       "Answer only with calculated result. Abstain from explanations or rephrasing the task!"
                       "You are given the values:"
                       "A = {{a}}; B = {{b}}; C = {{c}}"
                       "Solve the following equations:"
                       "{{tasks}}"
                       "{{grade}}"]
          :tasks      ["{{p1}}" "{{p2}}" "{{p3}}"]
          :p1         "A + B = {{x}}"
          :p2         "A - B = {{y}}"
          :p3         "({{x}} + {{y}}) / C = {{z}}"
          :eval1-role ["{{tasks}}"
                       "Evaluate if the solutions to the above equations are correct"
                       "{{eval1}}"]
          :eval2-role ["{{tasks}}"
                       "Evaluate if the solutions to the above equations are calulated optimaly"
                       "{{eval2}}"]
          :grade      ["Based on the following evaluations to math problems:"
                       "Evaluation A: {{eval1-role}}"
                       "Evaluation B: {{eval2-role}}"
                       "Based on this work grade (from 1 to 10) student's math knowledge."
                       "Give only grade number like '7' abstain from further explanations."
                       "{{score}}"]
          :x          solver
          :y          solver
          :z          solver
          :eval1      (llm :mistral wkk/model-params {:model :mistral-small :max-tokens 50})
          :eval2      (llm :mistral wkk/model-params {:model :mistral-small :max-tokens 50})
          :score      (llm :openai
                           wkk/output-format :number
                           wkk/model-params {:model :gpt-4 :max-tokens 2})})

  (generate g {:a 5 :b 2 :c 1})
