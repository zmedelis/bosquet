(ns bosquet.template.read-test
  (:require
   [bosquet.template.read :as read]
   [bosquet.template.tag :as tag]
   [clojure.test :refer [deftest is]]
   [matcher-combinators.matchers :as m]
   [matcher-combinators.test]))

(tag/add-tags)

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
       (read/template-vars "{{x}} + {{y}} = {% gen z %}")))
  (is (match?
       {:data-vars (m/in-any-order [:x :y])
        :gen-vars  [:bosquet/gen]}
       (read/template-vars "{{x}} + {{y}} = {% gen %}")))
  (is (match?
       {:data-vars [:x]
        :gen-vars  (m/in-any-order [:bosquet/gen])}
       (read/template-vars "{{x}} + {% gen %} =\n{% gen %}")))
  (is (match?
       {:data-vars [:x]
        :gen-vars  (m/in-any-order [:bosquet/gen :y])}
       (read/template-vars "{{x}}{%gen%}{%gen y%}"))))

(deftest ensure-gen-tag-test
  (is (= (str "So it begins {% " read/gen-tag-name " %}")
         (read/ensure-gen-tag "So it begins")))
  (is (= (str "So it begins {% " read/gen-tag-name " %}")
         (read/ensure-gen-tag "So it begins")))
  (is (= (str "So it begins {% " read/gen-tag-name " var-name %}")
         (read/ensure-gen-tag (str "So it begins {% " read/gen-tag-name " var-name %}"))))
  (is (= (str "{% " read/gen-tag-name " %} so it ends.")
         (read/ensure-gen-tag
          (str "{% " read/gen-tag-name " %} so it ends.")))))
