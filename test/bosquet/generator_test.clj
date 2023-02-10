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
         "{{openning}} {{subject}} ((bosquet.generator-test/dummy-generator))"
         :test-results
         "{{t/unit-test}} Test result summary: ((bosquet.generator-test/dummy-generator))"}

        {:t/keys [openning subject unit-test test-results] :bosquet/keys [completions] :as o}
        (complete prompts
          {:function1 "foo" :function2 "boo"}
          {:config "config1"})]
    (is (match? "You are the unit test." openning))
    (is (match? #".*foo.*?boo.*\." subject))
    (is (match? #"You.*'foo' 'boo'. Text: 53 Config: 1" unit-test))
    (is (match? #"You.*Test result.*1$" test-results))))
