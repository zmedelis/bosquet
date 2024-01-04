(ns math-generate-code
  (:require
   [bosquet.llm :as llm]
   [bosquet.llm.generator :as g]
   [helpers :as h]
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
               {:question (h/join
                            "Janet's ducks lay 16 eggs per day."
                            "She eats three for breakfast every morning and bakes muffins for her friends every day with four."
                            "She sells the remainder at the farmers' market daily for $2 per fresh duck egg."
                            "How much in dollars does she make every day at the farmers' market?")
                :code     "(* (- 16 3 4) 2)"}
               {:question "A robe takes 2 bolts of blue fiber and half that much white fiber. How many bolts in total does it take?"
                :code     "(+ 2 (/ 2 2)"}])

;; This is used in the Selmer template iterating over the examples. Note that this is only a snippet of the whole prompt to be assembled later.
;;
;; _`h/join` is a helper function to join strings with newlines_

(def few-shot
  (h/join
    "{% for example in examples %}"
    "QUESTION: {{example.question}}"
    "CODE: {{example.code}}"
    "{% endfor %}"))

;; ### Defining the prompt
;;
;; Few shot learning-based prompt needs to list the examples followed by the request to answer the
;; question. The bellow prompt is constructed using a separate few-shot section and calc section
;; that constructs the request to generate text in the following 'CODE:'.

(def prompt {:few-shot few-shot
             :calc     (h/join
                        "{{few-shot}}"
                        "QUESTION: {{question}}"
                        "CODE:")
             :answer   (g/llm :openai llm/context :calc)})

;;
;; Let's have two questions to generate code for.
;;

(def question1
  (h/join
    "Carla is downloading a 200 GB file. She can download 2 GB/minute, but 40% of the way through the download, the download fails."
    "Then Carla has to restart the download from the beginning. How long did it take her to download the file in minutes?"))

(def question2
  (h/join
    "Janet’s ducks lay 16 eggs per day. She eats three for breakfast every morning and bakes muffins for her friends every day with four."
    "She sells the remainder for $2 per egg. How much does she make every day?"))

;;
;; #### Question 1 answer
;;
(def q1-answer (g/generate prompt
                           {:examples examples :question question1}))

^{:nextjournal.clerk/visibility :fold}
(let [{answer :answer} q1-answer]
  (c/html
   [:div
    [:div "Code:" [:pre answer]]
    [:div "Eval:" [:pre (-> answer read-string eval)]]]))

;;
;; #### Question 2 answer
;;
^{:nextjournal.clerk/visibility :fold}
(let [{:keys [answer]} (g/generate prompt {:examples examples :question question2})]
  (c/html [:div
           [:div "Code:" [:pre answer]]
           [:div "Eval:" [:pre (-> answer read-string eval)]]]))


;; ### Footnote
;;
;; > The prompt definition above using references is not strictly needed and is there to demonstrate such a possiblility. It would allow you to define different calc keys (say you want to instruct differently)
;; by reusing the same few-shot. To simplify it could be done without references like that
;;
(h/join
 "{% for example in examples %}"
 "QUESTION: {{example.question}}"
 "CODE: {{example.code}}"
 "{% endfor %}"
 "QUESTION: {{question}}"
 "CODE:")
