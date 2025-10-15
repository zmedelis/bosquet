(ns bosquet.memory.simple-memory-test
  (:require
   [bosquet.memory.retrieval :as r]
   [bosquet.memory.simple-memory :as m]
   [clojure.test :as t]))

#_(t/deftest simple-memory-operations
    (let [mem (m/->remember)]
      (m/forget)
      (mem nil "1")
      (mem nil ["2" "3" "4" "5"])
    ;; no limits specified return all
      (t/is (= ["1" "2" "3" "4" "5"] (.sequential-recall mem {r/memory-content identity})))
    ;; last 3 objects returned, no token limit
      (t/is (= ["3" "4" "5"] (.sequential-recall mem {r/memory-content    identity
                                                      r/memory-objects-limit 3})))
    ;; object and token limitation
      (t/is (= ["4" "5"] (.sequential-recall mem {r/memory-objects-limit 3
                                                  r/memory-tokens-limit  3
                                                  r/memory-content    identity})))
      (t/is (= ["3" "4" "5"] (.sequential-recall mem {r/memory-objects-limit 3
                                                      r/memory-tokens-limit  1000
                                                      r/memory-content    identity})))))

(t/deftest cue-recall
  (let [mem        (m/->remember)
        cue        (m/->cue-memory)
        sim-params {r/content-similarity-threshold 0.3}]
    (m/forget)
    (mem nil ["This is a car" "This is a bar" "The sky is dark" "Dark is the sky"])
    (t/is (= ["This is a car" "This is a bar"]
             (cue sim-params "This is a fox")))
    (t/is (empty? (cue sim-params "Underground policemen's union")))))
