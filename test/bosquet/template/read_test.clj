(ns bosquet.template.read-test
  (:require
   [clojure.test :refer [deftest is]]
   [bosquet.template.read :as read]))

(deftest fill-slots-test
  (is (= "What is 1 + 2"
         (first (read/fill-slots "What is {{x}} + {{y}}" {:x 1 :y 2} nil)))))
