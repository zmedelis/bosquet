(ns bosquet.memory.retrieval-test
  (:require
   [bosquet.llm.openai :as openai]
   [bosquet.memory.retrieval :as r]
   [clojure.test :refer [deftest is]])
  (:import
   [bosquet.memory.simple_memory SimpleMemory]))

(deftest memory-object-size-test
  (is (= 1 (r/memory-object-size "A" "gpt-4" openai/openai)))
  (is (= 6 (r/memory-object-size "Call me Ishmael." "gpt-3.5-turbo" openai/openai))))

(defn- ->memory []
  (let [mem (SimpleMemory. (atom []))]
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
                                    r/memory-content-fn    identity})))))

(deftest retrieval-token-limit
  (let [mem (->memory)]
    (is (= ["Four monkeys" "Five monkeys"]
           (.sequential-recall mem {r/memory-tokens-limit 5
                                    r/memory-content-fn   identity})))))

(deftest cue-retrieval
  (let [mem (->memory)]
    (.remember mem "10 jumping donkeys" nil)
    (is (= ["Two monkeys" "Three monkeys" "Four monkeys" "Five monkeys"]
           (.cue-recall mem "Four monkeys"
                        {r/memory-tokens-limit 100
                         r/memory-content-fn identity})))))
