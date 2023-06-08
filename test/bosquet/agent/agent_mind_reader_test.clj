(ns bosquet.agent.agent-mind-reader-test
  (:require
   [clojure.test :refer [deftest is]]
   [bosquet.agent.agent-mind-reader :refer :all]))

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
Action 2: Search[James Stockdale]
Observation 2: ...")

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
          "Thought 1: I need to search David Chanoff, find the U.S. Navy admiral he
collaborated with, then find the President the admiral served under.
Action 1: Search[David Chanoff]"}
         (find-action 1 thought-search)))
  (is (= {:action     :search
          :parameters "James Stockdale"
          :thought
          "Thought 2: U.S. Navy Admiral James Stockdale served as the ambassador to the United Kingdom. I need to search James Stockdale and find which President he served under.
Action 2: Search[James Stockdale]"}
        (find-action 2 thought-search)))
  (is (= {:action     :lookup
          :parameters "named after"
          :thought
          "Thought 2: The paragraph does not tell who Milhouse is named after, maybe I can look up \"named after\".
Action 2: Lookup[named after]"}
        (find-action 2 thought-lookup))))

(deftest sentence-splitter
  (is (= ["Sentence one." "Sentence A.B. two?" "Last one!"]
         (split-sentences "Sentence one.\nSentence A.B. two? Last one!"))))

(deftest content-lookup-index
  (is (= [[0 true "This sentence one."]
          [1 true "This sentence A.B. two?"]
          [2 false "Almost the last sentence."]
          [3 true "The A.B. is good in this sentence!"]]
        (lookup-index
          "this Sentence"
          "This sentence one.\nThis sentence A.B. two? Almost the last sentence. The A.B. is good in this sentence!"))))
