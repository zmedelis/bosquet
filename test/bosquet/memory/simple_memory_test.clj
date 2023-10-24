(ns bosquet.memory.simple-memory-test
  (:require
   [bosquet.memory.retrieval :as r]
   [bosquet.memory.simple-memory :as m]
   [clojure.test :as t]))

(t/deftest simple-memory-operations
  (let [mem (m/->SimpleMemory (atom []))]
    (.remember mem "1" nil)
    (.remember mem ["2" "3" "4" "5"] nil)
    (t/is (= ["3" "4" "5"] (.sequential-recall mem {r/memory-objects-limit 3
                                                    r/memory-content-fn    identity})))))
