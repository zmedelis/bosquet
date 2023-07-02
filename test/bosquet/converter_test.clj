(ns bosquet.converter-test
  (:require
   [clojure.test :refer [deftest is]]
   [bosquet.converter :refer [numbered-items->list
                              yes-no->bool]]))

(deftest converting-numbered-lists
  (is (= ["foo" "bar" "baz"]
         (numbered-items->list "1. foo\n2. bar\n3. baz")))
  (is (= ["foo" "bar" "baz"]
         (numbered-items->list "\n\n1. foo\n2. bar\n3. baz"))))

(deftest converting-yes-and-noes
  (is (true? (yes-no->bool "yes")))
  (is (true? (yes-no->bool "YES")))
  (is (false? (yes-no->bool "     nO    ")))
  (is (false? (yes-no->bool "NO")))
  (is (nil? (yes-no->bool "X"))))
