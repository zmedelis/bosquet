(ns bosquet.converter-test
  (:require
   [clojure.test :refer [deftest is]]
   [bosquet.converter :refer [numbered-items->list]]))

(deftest converting-numbered-lists
  (is (= ["foo" "bar" "baz"]
        (numbered-items->list "1. foo\n2. bar\n3. baz")))
  (is (= ["foo" "bar" "baz"]
        (numbered-items->list "\n\n1. foo\n2. bar\n3. baz"))))
