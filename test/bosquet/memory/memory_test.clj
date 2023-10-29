(ns bosquet.memory.memory-test
  (:require
   [bosquet.memory.memory :as m]
   [bosquet.memory.retrieval :as r]
   [bosquet.wkk :as wkk]
   [clojure.test :refer [deftest is]]))

(deftest available-memories-test
  (is (= [:message1 :message2]
        ;; memory is not configured, return existing messages as is
         (m/available-memories {wkk/recall-function r/recall-sequential}
                               [:message1 :message2]))))
