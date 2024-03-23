(ns bosquet.memory.retrieval-test
  (:require
   [bosquet.llm.wkk :as wkk]
   [bosquet.memory.retrieval :as r]
   [bosquet.memory.simple-memory :as simple-memory]
   [clojure.test :refer [deftest is]]))

(deftest memory-object-size-test
  (is (= 1 (r/memory-object-size "A" "gpt-4" wkk/openai)))
  (is (= 6 (r/memory-object-size "Call me Ishmael." "gpt-3.5-turbo" wkk/openai))))

(defn- memorize []
  (let [mem (simple-memory/->remember)]
    (simple-memory/forget)
    (doseq [m ["One monkey" "Two monkeys" "Three monkeys"
               "Four monkeys" "Five monkeys"]]
      (mem nil m))))

(deftest cue-retrieval
  (memorize)
  ((simple-memory/->remember) nil "10 jumping donkeys")
  (is (= ["Four monkeys"]
         ((simple-memory/->cue-memory)
          {r/content-similarity-threshold 0.01}
          "Four monkeys"))))
