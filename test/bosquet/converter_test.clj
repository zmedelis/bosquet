(ns bosquet.converter-test
  (:require
   [bosquet.converter :as c]
   [bosquet.utils :as u]
   [clojure.test :refer [deftest is testing]]))

(deftest converting-lists
  (testing "numbered lists and edge cases"
    (is (= ["foo" "bar" "baz"]
           (c/list-reader "1. foo\n2. bar\n3. baz")))
    (is (= ["foo1.1" "bar" "baz"]
           (c/list-reader "1. foo1.1 \n2. bar\n3. baz")))
    (is (= ["foo" "bar" "baz"]
           (c/list-reader "\n\n1. foo\n2. bar\n3. baz"))))
  (testing "numbered unordered lists"
    (is (= ["foo" "bar" "baz"]
           (c/list-reader "* foo\n* bar\n* baz")))
    (is (= ["foo" "bar" "baz"]
           (c/list-reader "- foo\n- bar\n- baz")))))

(deftest converting-yes-and-noes
  (is (true? (c/yes-no->bool "yes")))
  (is (true? (c/yes-no->bool "YES")))
  (is (false? (c/yes-no->bool "     nO    ")))
  (is (false? (c/yes-no->bool "NO")))
  (is (= "X" (c/yes-no->bool "X"))))

(deftest coerce-test
  (is (= "Dogs are great!" (c/coerce nil "Dogs are great!")))
  (is (= "Dogs are great!" (c/coerce :pdf "Dogs are great!")))
  (is (= [{"x" 1.2 "y" 0.8}] (c/coerce :json "[{\"x\" : 1.2, \"y\" : 0.8}]"))))

(deftest reading-edn
  (is (= [1 2] (c/edn-reader "[1 2]")))
  (is (= [1 2] (c/edn-reader (u/join-lines "```clojure" "[1 2]" "```"))))
  (is (= :edn (c/edn-reader (u/join-lines "```edn" ":edn" "```")))))

(deftest reading-json
  (is (= [1 2] (c/json-reader "[1, 2]")))
  (is (= {"a" 2} (c/json-reader (u/join-lines "Junk\nmore of it\n```json" "{\"a\" : 2}" "```\njunk"))))
  (is (= 1 (c/json-reader (u/join-lines "```json" "1" "```")))))

(deftest converting-numbers
  (is (= "x" (c/string->number "x")))
  (is (= 1 (c/string->number "1")))
  (is (= 1.1 (c/string->number "1.1")))
  (is (= 101.09 (c/string->number "101.09")))
  (is (= 0.1 (c/coerce :number "0.100"))))

(deftest failing-conversions
  (is (= "[1" (c/coerce :json "[1"))))
