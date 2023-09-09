(ns bosquet.generator-test
  (:require
   [bosquet.llm.generator :refer [generate all-keys]]
   [bosquet.llm.openai :as openai]
   [clojure.test :refer [deftest is]]
   [matcher-combinators.matchers :as m]
   [matcher-combinators.test :refer [match?]]))

(defn dummy-generator [_text {model :model}]
  (condp = model
    "galileo" "0.0017 AU"
    "hubble"  "Yes"
    (throw (ex-info (str "Unknown model: " model) {}))))

(def astronomy-prompt
  {:role            "As a brilliant {{you-are}} answer the following question."
   :question-answer "Question: {{question}} Answer: {% gen var-name=answer model=galileo %}"
   :self-eval       "{{answer}}. Is this a correct answer? {% gen var-name=test model=hubble %}"})

(deftest keys-to-produce
  (is (match? (m/in-any-order [:role :question :question-answer :self-eval :you-are :answer :test])
              (all-keys astronomy-prompt {:you-are "astronomer" :question "How far to X?"}))))

(deftest generation-with-different-models
  (is
   (match?
    {:role            "As a brilliant astronomer answer the following question."
     :question        "What is the distance from Moon to Io?"
     :question-answer "Question: What is the distance from Moon to Io? Answer: 0.0017 AU"
     :self-eval       "0.0017 AU. Is this a correct answer? Yes"
     :test            "Yes"
     :you-are         "astronomer"}
    (with-redefs [openai/complete dummy-generator]
      (generate astronomy-prompt
                {:you-are  "astronomer"
                 :question "What is the distance from Moon to Io?"})))))

(deftest fail-generation
  (is (match?
       {:role            "As a brilliant astronomer answer the following question."
        :question        "What is the distance from Moon to Io?"
        :question-answer "Question: What is the distance from Moon to Io? Answer: 0.0017 AU"
        :self-eval       nil
        :test            nil
        :you-are         "astronomer"}
       (with-redefs [openai/complete dummy-generator]
         (generate
          (assoc astronomy-prompt :self-eval "{% gen var-name=test model=AGI %}")
          {:you-are  "astronomer"
           :question "What is the distance from Moon to Io?"})))))
