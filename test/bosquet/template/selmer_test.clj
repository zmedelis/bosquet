(ns bosquet.template.selmer-test
  (:require
   [clojure.test :refer [deftest is]]
   [bosquet.template.selmer :refer [clear-gen-var-slot]]))

(deftest clearing-after-gen-slot
  (is (= "" (clear-gen-var-slot "{{x}}" :x)))
  (is (= "" (clear-gen-var-slot "{{x}} = 10" :x)))
  (is (= "" (clear-gen-var-slot "{{x}} = 10" :x)))
  (is (= "{{x}} = " (clear-gen-var-slot "{{x}} = {{y}}" :y)))
  (is (= "{{x}}\n=\n" (clear-gen-var-slot "{{x}}\n=\n{{y}}" :y)))
  (is (= "{{o/x}}\n=" (clear-gen-var-slot "{{o/x}}\n={{o/y}} DONE" :o/y))))
