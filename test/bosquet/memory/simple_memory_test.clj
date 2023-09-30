(ns bosquet.memory.simple-memory-test
  (:require
   [bosquet.memory.retrieval :as r]
   [bosquet.memory.simple-memory :as m]
   [clojure.test :as t]))

(t/deftest simple-memory-operations
  (let [mem (m/->SimpleMemory (atom []) identity)]
    (.remember mem "1")
    (.remember mem ["2" "3" "4" "5"])
    (t/is (= ["3" "4" "5"] (.sequential-recall mem {r/memory-objects-limit 3
                                                    r/memory-content-fn    identity})))))
