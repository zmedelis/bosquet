(ns bosquet.template-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [bosquet.template :as tpl]))

(deftest slot-extraction
  (testing "filling in plain vars"
    (is (= #{:x :ns.one/y}
          (tpl/slots-required "{{x}} {{ns..one/y}}"))))
  (testing "filling in vars with config"
    (is (= #{:const}
          (tpl/slots-required "{{const|default:3.14}}")))
    (is (= #{:math/const}
          (tpl/slots-required "{{math/const|default:3.14}}"))))
  (testing "slots in for loops"
    (is (= #{:x :text}
          (tpl/slots-required "{{x}} {% for t in text %} {% endfor %}")))))

(deftest missing-values
  (is (= "1 = {{y}}"
        (tpl/fill-slots "{{x}} = {{y}}" {:x 1}))))

(deftest load-palette
  (let [palette {:full-spec {:prompt      "Hard to say at the"
                             :description "Full spec prompt"}
                 :string    "In the following diagram"}]
    (is (= "In the following diagram"
          (tpl/prompt-template (palette :string))))
    (is (= "Hard to say at the"
          (tpl/prompt-template (palette :full-spec))))))
