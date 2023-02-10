(ns bosquet.generator-test
  (:require
    [clojure.test :refer [deftest is]]
    [matcher-combinators.test :refer [match?]]
    [bosquet.generator :refer [complete]]))

#_:clj-kondo/ignore
(defn dummy-generator [text config]
  (str "Text: " (count text) " Config: " (count (keys config))))

(deftest bosquet.generator-test
  (let [prompts
        {:openning "You are the unit test."
         :subject  "Write a test for '{{function1}}' '{{function2}}'."
         :unit-test
         "{{openning}} {{subject}} {% llm-generate model=text-ada-001 %}"
         :test-results
         "{{t/unit-test}} Test result summary: {% llm-generate model=text-ada-001 %}"}

        {:t/keys [openning subject unit-test test-results]}
        (complete prompts
          {:function1 "foo" :function2 "boo"})]
    (is (match? "You are the unit test." openning))
    (is (match? #".*foo.*?boo.*\." subject))
    (is (match? #"You.*'foo' 'boo'. Text: 53 Config: 1" unit-test))
    (is (match? #"You.*Test result.*1$" test-results))))
