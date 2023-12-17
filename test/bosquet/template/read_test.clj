(ns bosquet.template.read-test
  (:require
   [clojure.test :refer [deftest is]]
   [bosquet.template.read :as read]))

(deftest fill-slots-test
  (is (= "What is 1 + 2"
         (first (read/fill-slots "What is {{x}} + {{y}}" {:x 1 :y 2} nil)))))

(deftest var-extraction
  (is (= {:data-slots #{:x :y}
          :gen-vars   #{}}
         (read/template-vars "{{x}} + {{y}}")))
  (is (= {:data-slots #{:x :y}
          :gen-vars   #{:z}}
         (read/template-vars "{{x}} + {{y}} = {% gen2 z %}")))
  (is (= {:data-slots #{:x :y}
          :gen-vars   #{:bosquet/gen-1}}
         (read/template-vars "{{x}} + {{y}} = {% gen2 %}")))
  (is (= {:data-slots #{:x}
          :gen-vars   #{:bosquet/gen-1 :bosquet/gen-2}}
         (read/template-vars "{{x}} + {% gen2 %} = {% gen2 %}")))
  (is (= {:data-slots #{:x}
          :gen-vars   #{:bosquet/gen-1 :y}}
         (read/template-vars "{{x}}{%gen2%}{%gen2 y%}"))))
