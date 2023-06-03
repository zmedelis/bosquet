(ns bosquet.agent.agent-mind-reader-test
  (:require
   [clojure.test :refer [deftest is]]
   [bosquet.agent.agent-mind-reader :refer [find-action split-sentences]]))

(def ^:private thought-search
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

(def ^:private thought-lookup
  "Observation 1: Milhouse Mussolini Van Houten is a recurring character in the Fox animated
television series The Simpsons voiced by Pamela Hayden and created by Matt Groening.
Thought 2: The paragraph does not tell who Milhouse is named after, maybe I can look up \"named after\".
Action 2: Lookup[named after]
Observation 2: (Result 1 / 1) Milhouse was named after U.S. president Richard Nixon, whose middle name was Milhous.")

(deftest find-first-action-test
  (is (= {:action     :search
          :parameters "David Chanoff"
          :thought
          "I need to search David Chanoff, find the U.S. Navy admiral he
collaborated with, then find the President the admiral served under."}
         (find-action 1 thought-search)))
  (is (= {:action     :lookup
          :parameters "named after"
          :thought
          "The paragraph does not tell who Milhouse is named after, maybe I can look up \"named after\"."}
        (find-action 2 thought-lookup))))

(deftest sentence-splitter
  (is (= ["Sentence one." "Sentence A.B. two?"]
         (split-sentences "Sentence one. Sentence A.B. two?"))))
