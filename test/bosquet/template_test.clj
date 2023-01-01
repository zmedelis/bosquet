(ns bosquet.template-test
  (:require
    [clojure.test :refer [deftest is]]
    [bosquet.template :as tpl]))

(deftest slots-required-extraction
  (is (= [:x :ns.one/y]
        (tpl/slots-required "{{x}} {{ns..one/y}}"))))

(deftest missing-values
  (is (= "1 = {{y}}"
        (tpl/fill-text-slots "{{x}} = {{y}}" {:x 1}))))
