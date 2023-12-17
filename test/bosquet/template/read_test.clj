(ns bosquet.template.read-test
  (:require
   [matcher-combinators.matchers :as m]
   [matcher-combinators.test]
   [clojure.test :refer [deftest is]]
   [bosquet.template.read :as read]))

(deftest fill-slots-test
  (is (= "What is 1 + 2"
         (first (read/fill-slots "What is {{x}} + {{y}}" {:x 1 :y 2} nil)))))

(deftest var-extraction
  (is (match?
       {:data-vars (m/in-any-order [:x :y])
        :gen-vars  []}
       (read/template-vars "{{x}} + {{y}}")))
  (is (match?
       {:data-vars (m/in-any-order [:x :y])
        :gen-vars  [:z]}
       (read/template-vars "{{x}} + {{y}} = {% gen2 z %}")))
  (is (match?
       {:data-vars (m/in-any-order [:x :y])
        :gen-vars  [:bosquet/gen-1]}
       (read/template-vars "{{x}} + {{y}} = {% gen2 %}")))
  (is (match?
       {:data-vars [:x]
        :gen-vars  (m/in-any-order [:bosquet/gen-1 :bosquet/gen-2])}
       (read/template-vars "{{x}} + {% gen2 %} = {% gen2 %}")))
  (is (match?
       {:data-vars [:x]
        :gen-vars  (m/in-any-order [:bosquet/gen-1 :y])}
       (read/template-vars "{{x}}{%gen2%}{%gen2 y%}"))))
