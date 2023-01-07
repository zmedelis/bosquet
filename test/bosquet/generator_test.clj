(ns bosquet.generator-test
  (:require
    [clojure.test :refer [deftest is]]
    [bosquet.generator :refer [complete]]))

#_:clj-kondo/ignore
(defn dummy-generator [text config] (str "Text: " (count text) " Config: " (count (keys config))))

(deftest bosquet.generator-test
  (let [prompts
        {:t/openning "You are the unit test."
         :t/subject  "Write a test for {{function1}} {{function2}}."
         :t/code     "{{t/openning}} {{t/subject}} ((bosquet.generator-test/dummy-generator))"}]
    (is (= {:t/code
            "You are the unit test. Write a test for foo boo. Text: 49 Config: 1"
            :completion/generated-text
            "Text: 49 Config: 1"}
           (complete prompts
             {:function1 "foo" :function2 "boo"}
             [:t/code :completion/generated-text]
             {:config "config1"})))))
