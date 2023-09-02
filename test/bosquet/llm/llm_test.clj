(ns bosquet.llm.llm-test
  (:require
   [clojure.test :refer [deftest is]]
   [bosquet.llm.llm :refer [model-mapping]]))

(deftest test-model-mapping
  (is (= :y (model-mapping {:model-name-mapping {:x :y}} :x)))
  (is (= :z (model-mapping {:model-name-mapping {:x :y}} :z))))
