(ns bosquet.converter-test
  (:require
   [clojure.test :refer [deftest is]]
   [bosquet.converter :as c]))

(deftest converting-numbered-lists
  (is (= ["foo" "bar" "baz"]
         (c/numbered-items->list "1. foo\n2. bar\n3. baz")))
  (is (= ["foo" "bar" "baz"]
         (c/numbered-items->list "\n\n1. foo\n2. bar\n3. baz"))))

(deftest converting-yes-and-noes
  (is (true? (c/yes-no->bool "yes")))
  (is (true? (c/yes-no->bool "YES")))
  (is (false? (c/yes-no->bool "     nO    ")))
  (is (false? (c/yes-no->bool "NO")))
  (is (nil? (c/yes-no->bool "X"))))

(deftest coerce-test
  (is (= "Dogs are great!" (c/coerce "Dogs are great!" nil)))
  (is (= "Dogs are great!" (c/coerce "Dogs are great!" :pdf)))
  (is (= [{"x" 1.2 "y" 0.8}] (c/coerce "[{\"x\" : 1.2, \"y\" : 0.8}]" :json))))
