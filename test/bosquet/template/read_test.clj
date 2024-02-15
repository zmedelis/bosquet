(ns bosquet.template.read-test
  (:require
   [bosquet.llm.wkk :as wkk]
   [bosquet.template.read :refer [data-slots]]
   [clojure.test :refer [deftest is]]))

(deftest data-slots-test
  (is #{:v1 :v2}
      (data-slots [[:user "ab {{v1}}"]
                   [:assistant {:llm :test wkk/var-name :ref1}]
                   [:user ["1" "2 {{v2}} {{ref1}}"]]]))
  (is (= #{:v1} (data-slots {:x "aa" :y "bb {{v1}}" :z "{{y}}"})))
  (is (= #{:data1 :data2} (data-slots {:q "{{data1}} {{data2}} = {{a}}"
                                       :a {:llm :oai}}))))
