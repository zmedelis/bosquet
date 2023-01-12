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
        {:t/openning "You are the unit test."
         :t/subject  "Write a test for '{{function1}}' '{{function2}}'."
         :t/unit-test
         "{{t/openning}} {{t/subject}} ((bosquet.generator-test/dummy-generator))"
         :t/test-results
         "{{t/unit-test}} Test result summary: ((bosquet.generator-test/dummy-generator))"}

        {:t/keys [openning subject unit-test test-results] :bosquet/keys [completions] :as o}
        (complete prompts {:function1 "foo" :function2 "boo"} {:config "config1"}
          [:t/openning :t/subject :t/unit-test :t/test-results
           :bosquet/completions])]
    (is (match? "You are the unit test." openning))
    (is (match? #".*foo.*?boo.*\." subject))
    (is (match? #"You.*'foo' 'boo'. Text: 53 Config: 1" unit-test))
    (is (match? #"You.*Test result.*1$" test-results))
    (is (= {:t/unit-test "Text: 53 Config: 1"
            :t/test-results "Text: 49 Config: 1"}
          completions))))
