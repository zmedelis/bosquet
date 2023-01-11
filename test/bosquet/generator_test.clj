(ns bosquet.generator-test
  (:require
    [clojure.test :refer [deftest is]]
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
         "{{function1}} - PASSED; {{function2}} - FAILED. Test result summary: ((bosquet.generator-test/dummy-generator))"}

        {:t/keys [openning subject unit-test test-results] :bosquet/keys [completions]}
        (complete prompts {:function1 "foo" :function2 "boo"} {:config "config1"}
                  [:t/openning :t/subject :t/unit-test :t/test-results
                   :bosquet/completions])]

    (is (= openning "You are the unit test."))
    (is (= subject "Write a test for 'foo' 'boo'."))
    (is (= unit-test "You are the unit test. Write a test for 'foo' 'boo'. Text: 53 Config: 1"))
    (is (= test-results "foo - PASSED; boo - FAILED. Test result summary: Text: 49 Config: 1"))
    (is (= completions {:t/unit-test    "Text: 53 Config: 1"
                        :t/test-results "Text: 49 Config: 1"}))))
