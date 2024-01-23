(ns bosquet.template.read-test
  (:require
   [bosquet.template.read :as read]
   [clojure.test :refer [deftest is]]
   [matcher-combinators.test]))

(deftest fill-slots-test
  (is (= "What is 1 + 2"
         (first (read/fill-slots "What is {{x}} + {{y}}" {:x 1 :y 2} nil)))))


(deftest clearing-after-gen-slot
  (is (= "" (read/clear-gen-var-slot "{{x}}" :x)))
  (is (= "" (read/clear-gen-var-slot "{{x}} = 10" :x)))
  (is (= "" (read/clear-gen-var-slot "{{x}} = 10" :x)))
  (is (= "{{x}} = " (read/clear-gen-var-slot "{{x}} = {{y}}" :y)))
  (is (= "{{x}}\n=\n" (read/clear-gen-var-slot "{{x}}\n=\n{{y}}" :y))))
