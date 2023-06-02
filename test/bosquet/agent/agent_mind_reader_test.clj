(ns bosquet.agent.agent-mind-reader-test
  (:require
   [clojure.test :refer [deftest is]]
   [bosquet.agent.agent-mind-reader :refer [find-first-action]]))

(def thought-1
  "Question: Author David Chanoff has collaborated with a U.S. Navy admiral who served as the ambassador to
the United Kingdom under which President?
Thought 1: I need to search David Chanoff, find the U.S. Navy admiral he
collaborated with, then find the President the admiral served under.
Action 1: Search[David Chanoff]
Observation 1: David Chanoff is an American author and journalist who has written or co-written
over 20 books. He is best known for his collaborations with U.S. Navy
Admiral James Stockdale.
Thought 2: U.S. Navy Admiral James Stockdale served as the ambassador to the United Kingdom. I need to search James Stockdale and find which President he served under.
Action 2: Search[James Stockdale]")

(deftest find-first-action-test
  (is (= {:action     :search
          :parameters "David Chanoff"
          :thought
          "I need to search David Chanoff, find the U.S. Navy admiral he
collaborated with, then find the President the admiral served under."}
        (find-first-action thought-1))))
