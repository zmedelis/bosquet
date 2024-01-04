(ns bosquet.llm-test
  (:require
   [clojure.test :refer [deftest is]]
   [bosquet.llm :as llm]))

#_(deftest picking-the-right-handler
    (is (= {:model  :raccoon
            :called true}
           (llm/chat {}
                     {llm/service :umbrella-corp :model :raccoon}
                     {:umbrella-corp (fn [_config props] (assoc props :called true))}))))