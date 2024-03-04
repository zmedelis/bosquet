(ns bosquet.llm.oai-shaped-llm-test
  (:require
   [bosquet.llm.oai-shaped-llm :as oai]
   [bosquet.llm.wkk :as wkk]
   [clojure.test :refer [deftest is]]))

(deftest prep-params-test
  (is (= {:model :default-model}
         (oai/prep-params {} :default-model)))
  (is (= {:max-tokens 10}
         (oai/prep-params {:max-tokens 10})))
  (is (= {:cache      true
          :model      :gpt-10
          :max-tokens 1}
         (oai/prep-params
          {:cache           true
           wkk/model-params {:model :gpt-10 :max-tokens 1}}
          :default-model))))
