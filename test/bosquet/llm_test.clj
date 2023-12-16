(ns bosquet.llm-test
  (:require
   [clojure.test :refer [deftest is]]
   [bosquet.llm :as llm]))

(deftest picking-the-right-handler
  (is (= {:model  :raccoon
          :called true}
         (llm/chat {llm/provider :umbrella-corp} {:model :raccoon}
                   {:umbrella-corp (fn [_config props] (assoc props :called true))}))))
