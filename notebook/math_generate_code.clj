(ns math-generate-code
  (:require
   [bosquet.llm.generator :as g]
   [bosquet.llm.wkk :as wkk]
   [bosquet.utils :as u]
   [nextjournal.clerk :as c]))

;; ## Code generation for math calculations
;;
;; > Based on https://github.com/outlines-dev/outlines/blob/main/examples/math_generate_code.py
;;
;; This example shows how to construct a few-shot learning prompt to generate code for math calculations.
;; This relies on Selmer for loop construct to iterate over the examples and Bosquet gen tag to generate the code.

;; ### Defining few-shot examples
;;
;; Examples are defined as collections containing example data points.
;;
(def examples [{:question "What is 37593 * 67?"
                :code     "(* 37593 67)"}
               {:question (u/join-lines
                           "Janet's ducks lay 16 eggs per day."
                           "She eats three for breakfast every morning and bakes muffins for her friends every day with four."
                           "She sells the remainder at the farmers' market daily for $2 per fresh duck egg."
                           "How much in dollars does she make every day at the farmers' market?")
                :code     "(* (- 16 3 4) 2)"}
               {:question "A robe takes 2 bolts of blue fiber and half that much white fiber. How many bolts in total does it take?"
                :code     "(+ 2 (/ 2 2)"}])

;; This is used in the Selmer template iterating over the examples. See `:calc` in `prompt` map below.
;;
;; ### Defining the prompt
;;
;; Few shot learning-based prompt needs to list the examples followed by the request to answer the
;; question. The bellow prompt is constructed using a separate few-shot section and calc section
;; that constructs the request to generate text in the following 'CODE:'.

(def prompt {:calc     ["{% for example in examples %}"
                        "QUESTION: {{example.question}}"
                        "CODE: {{example.code}}"
                        "{% endfor %}"
                        ""
                        "QUESTION: {{question}}"
                        "CODE: {{answer}}"]
             :answer   (g/llm :openai wkk/model-params {:model :gpt-4})})

;;
;; Let's have two questions to generate code for.
;;

;; #### Question 1

(def question1
  (u/join-lines
   "Carla is downloading a 200 GB file. She can download 2 GB/minute, but 40% of the way"
   "through the download, the download fails."
   "Then Carla has to restart the download from the beginning. How long did it take"
   "her to download the file in minutes?"))

^{:nextjournal.clerk/visibility :fold}
(let [{{answer :answer} g/completions}
      (g/generate prompt {:examples examples :question question1})]
  (c/html
   [:div
    [:div "Code:" [:pre answer]]
    [:div "Eval:" [:pre (-> answer read-string eval)]]]))

;; #### Question 2 answer

(def question2
  (u/join-lines
   "Janet’s ducks lay 16 eggs per day. She eats three for breakfast every morning and"
   "bakes muffins for her friends every day with four."
   "She sells the remainder for $2 per egg. How much does she make every day?"))

^{:nextjournal.clerk/visibility :fold}
(let [{{answer :answer} g/completions}
      (g/generate prompt {:examples examples :question question2})]
  (c/html [:div
           [:div "Code:" [:pre answer]]
           [:div "Eval:" [:pre (-> answer read-string eval)]]]))
