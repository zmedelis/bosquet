(ns bosquet.template-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [bosquet.template.read :as tpl]))

(deftest slot-extraction
  (testing "filling in plain vars"
    (is (= #{:x :ns.one/y}
           (tpl/slots-required "{{x}} {{ns..one/y}}"))))
  (testing "filling in vars with config"
    (is (= #{:const}
           (tpl/slots-required "{{const|default:3.14}}")))
    (is (= #{:math/const}
           (tpl/slots-required "{{math/const|default:3.14}}"))))
  (testing "slots with config params"
    (is (= #{:x}
           (tpl/slots-required "{% debug var-name=test x=y %} {{x}}"))))
  (testing "slots in for loops"
    (is (= #{:x :text}
           (tpl/slots-required "{{x}} {% for t in text %} {% endfor %}"))))
  (testing "generation slot extraction"
    (is (= #{:test1 :test2}
           (tpl/generation-vars "{% debug var-name=test1 %} {% debug var-name=test2 %}")))))
