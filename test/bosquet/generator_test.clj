(ns bosquet.generator-test
  (:require
    [clojure.test :refer [deftest is]]
    [bosquet.generator :refer [complete]]))

(defn dummy-generator [_text] "123")

(deftest bosquet.generator-test
  (let [prompts
        {:t/openning "You are the unit test."
         :t/subject  "Write a test for {{function1}} {{function2}}."
         :t/code     "{{t/openning}} {{t/subject}} ((bosquet.generator-test/dummy-generator))"}]
    (is (= {:t/code           "You are the unit test. Write a test for foo boo. 123"
            :t/generated-text "123"}
           (complete prompts
             {:function1 "foo" :function2 "boo"}
             ;; TODO this is not right, if the seqence is reverted
             ;; `genereated-text` is not produced
             [:t/code :t/generated-text])))))
