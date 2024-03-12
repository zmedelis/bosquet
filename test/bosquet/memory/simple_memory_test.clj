(ns bosquet.memory.simple-memory-test
  (:require
   [bosquet.memory.retrieval :as r]
   [bosquet.memory.simple-memory :as m]
   [clojure.test :as t]))

(t/deftest simple-memory-operations
  (let [mem (m/->SimpleMemory)]
    (.remember mem "1" nil)
    (.remember mem ["2" "3" "4" "5"] nil)
    ;; no limits specified return all
    (t/is (= ["1" "2" "3" "4" "5"] (.sequential-recall mem {r/memory-content-fn identity})))
    ;; last 3 objects returned, no token limit
    (t/is (= ["3" "4" "5"] (.sequential-recall mem {r/memory-content-fn    identity
                                                    r/memory-objects-limit 3})))
    ;; object and token limitation
    (t/is (= ["4" "5"] (.sequential-recall mem {r/memory-objects-limit 3
                                                r/memory-tokens-limit  3
                                                r/memory-content-fn    identity})))
    (t/is (= ["3" "4" "5"] (.sequential-recall mem {r/memory-objects-limit 3
                                                    r/memory-tokens-limit  1000
                                                    r/memory-content-fn    identity})))))

(t/deftest cue-recall
  (let [mem        (m/->SimpleMemory)
        sim-params {r/memory-content-fn            identity
                    r/content-similarity-threshold 0.3}]
    (.remember mem ["This is a car" "This is a bar" "The sky is dark" "Dark is the sky"] {})
    (t/is (= ["This is a car" "This is a bar"]
             (.cue-recall mem sim-params "This is a fox")))
    (t/is (empty? (.cue-recall mem sim-params "Underground policemen's union")))))
