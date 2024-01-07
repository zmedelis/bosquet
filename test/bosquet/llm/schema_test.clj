(ns bosquet.llm.schema-test
  (:require
   [clojure.test :refer [deftest is]]
   [bosquet.llm.schema :refer [model-mapping]]))

(deftest test-model-mapping
  (is (= :y (model-mapping {:model-name-mapping {:x :y}} :x)))
  (is (= :z (model-mapping {:model-name-mapping {:x :y}} :z))))
