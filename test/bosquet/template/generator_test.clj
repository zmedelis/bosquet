(ns bosquet.template.generator-test
  (:require
    [bosquet.generator :refer [complete]]
    [bosquet.openai :as openai]
    [clojure.test :refer [deftest is]]))

(defn dummy-generator [text config]
  (if (= (:model config) "galileo")
    "0.0017 AU"
    "Yes"))

(deftest bosquet.generator-test
  (let [prompt
        {:role
         "As a brilliant {{you-are}} answer the following question."
         :question
         "What is the distance between Io and Europa?"
         :question-answer
         "Question: {{question}} Answer: {% llm-generate var-name=answer model=galileo %}"
         :self-eval
         "{{answer}}. Is this a correct answer? {% llm-generate var-name=test model=hubble %}"}
        {:keys [role question question-answer self-eval test]}
        (with-redefs [openai/complete dummy-generator]
          (complete prompt
            {:you-are "astronomer"
             :question "What is the distance from Moon to Io?"}))]
    (is (= role "As a brilliant astronomer answer the following question."))
    (is (= question "What is the distance from Moon to Io?"))
    (is (= question-answer "Question: What is the distance from Moon to Io? Answer: 0.0017 AU"))
    (is (= self-eval "0.0017 AU. Is this a correct answer? Yes"))
    (is (= test "Yes"))))
