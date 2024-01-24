(ns bosquet.llm.gen-data-test
  (:require
   [bosquet.llm.gen-data :refer [reduce-gen-graph total-usage]]
   [clojure.test :refer [deftest is]]))

(deftest usage-aggregation
  (is (= {:total 15 :completion 12 :prompt 3}
         (total-usage
          {:x {:total 10 :completion 8 :prompt 2}
           :y {:total 5 :completion 4 :prompt 1}}))))

(deftest gen-graph-reduction
  (is (= {:y 100}
         (reduce-gen-graph (fn [m k _v] (assoc m k 100))
                           {:x "do not change me"
                            :y {:llm/gen 10}}))))
