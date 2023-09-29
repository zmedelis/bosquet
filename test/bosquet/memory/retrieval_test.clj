(ns bosquet.memory.retrieval-test
  (:require
   [bosquet.llm.openai :as openai]
   [bosquet.memory.encoding :as encoding]
   [bosquet.memory.retrieval :as r]
   [clojure.test :refer [deftest is]])
  (:import
   [bosquet.memory.simple_memory SimpleMemory]))

(deftest memory-object-size-test
  (is (= 1 (r/memory-object-size "A" "gpt-4" openai/openai)))
  (is (= 6 (r/memory-object-size "Call me Ishmael." "gpt-3.5-turbo" openai/openai))))

(defn- ->memory []
  (let [mem (SimpleMemory. (atom []) encoding/as-is-handler)]
    (.remember mem "One monkey")
    (.remember mem "Two monkeys")
    (.remember mem "Three monkeys")
    (.remember mem "Four monkeys")
    (.remember mem "Five monkeys")
    mem))

(deftest sequential-retrieval
  (let [mem (->memory)]
    (is (= ["One monkey" "Two monkeys" "Three monkeys" "Four monkeys" "Five monkeys"]
           (.sequential-recall mem nil)))
    (is (= ["Four monkeys" "Five monkeys"]
           (.sequential-recall mem
                               {r/memory-objects-limit 2})))))

(deftest retrieval-token-limit
  (let [mem (->memory)]
    (is (= ["Four monkeys" "Five monkeys"]
           (.sequential-recall mem
                               {r/memory-tokens-limit 5})))))
