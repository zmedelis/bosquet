(ns bosquet.memory.retrieval-test
  (:require
   [bosquet.memory.simple-memory]
   [bosquet.llm.wkk :as wkk]
   [bosquet.memory.retrieval :as r]
   [clojure.test :refer [deftest is]])
  (:import
   [bosquet.memory.simple_memory SimpleMemory]))

(deftest memory-object-size-test
  (is (= 1 (r/memory-object-size "A" "gpt-4" wkk/openai)))
  (is (= 6 (r/memory-object-size "Call me Ishmael." "gpt-3.5-turbo" wkk/openai))))

(defn- ->memory []
  (let [mem (SimpleMemory.)]
    (.forget mem nil)
    (.remember mem "One monkey" nil)
    (.remember mem "Two monkeys" nil)
    (.remember mem "Three monkeys" nil)
    (.remember mem "Four monkeys" nil)
    (.remember mem "Five monkeys" nil)
    mem))

(deftest sequential-retrieval
  (let [mem (->memory)]
    (is (= ["One monkey" "Two monkeys" "Three monkeys" "Four monkeys" "Five monkeys"]
           (.sequential-recall mem nil)))
    (is (= ["Four monkeys" "Five monkeys"]
           (.sequential-recall mem {r/memory-objects-limit 2
                                    r/memory-content    identity})))))

(deftest retrieval-token-limit
  (let [mem (->memory)]
    (is (= ["Four monkeys" "Five monkeys"]
           (.sequential-recall mem {r/memory-tokens-limit 5
                                    r/memory-content   identity})))))

(deftest cue-retrieval
  (let [mem (->memory)]
    (.remember mem "10 jumping donkeys" nil)
    (is (= ["Four monkeys"]
           (.cue-recall mem {r/memory-tokens-limit          100
                             r/memory-content            identity
                             r/content-similarity-threshold 0.01}
                        "Four monkeys")))))
