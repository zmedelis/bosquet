(ns bosquet.llm.cohere-test
  (:require
   [bosquet.llm.cohere :refer [complete]]
   [bosquet.llm.wkk :as wkk]
   [cohere.client :as client]
   [clojure.test :refer [deftest is]]))

(deftest complete-test
  (is (= {wkk/generation-type :completion
          wkk/content         {:completion "5"}
          wkk/usage           {:prompt 7 :completion 1 :total 8}}
         (with-redefs [client/generate
                       (fn [_]
                         {:generations [{:text "5"}]
                          :meta        {:billed_units {:input_tokens  7
                                                       :output_tokens 1}}})]
           (complete "2 + 2 =")))))
