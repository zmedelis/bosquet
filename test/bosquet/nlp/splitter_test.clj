(ns bosquet.nlp.splitter-test
  (:require
   [bosquet.llm.openai-tokens :as oai]
   [bosquet.nlp.splitter :as split]
   [clojure.test :refer [deftest is]]))

(deftest max-tokens-under
  ;; make sure than no chunk is larger than MAX tokens
  (let [MAX 4 model "gpt-4"]
    (is (> MAX
           (apply max
                  (->>
                   (split/split-max-tokens
                    "Talks from the AI Engineer Summit, October 8-10 2023"
                    MAX model)
                   (map #(count (oai/encode % model)))))))))

(deftest splitting-by-tokens
  (is (= ["Think not, is my eleventh commandment;"
          "ment; and sleep when you can, is my"
          " is my twelfth."]
         (split/chunk-text
          {split/chunk-size 10
           split/overlap    2
           split/split-unit split/token
           split/model      :gpt-4}
          "Think not, is my eleventh commandment; and sleep when you can, is my twelfth."))))

(deftest splitting-by-characters
  (is (= ["Never attempt to win by force "
          "what can be won by deception"]
         (split/chunk-text
          {split/chunk-size 30 split/split-unit split/character}
          "Never attempt to win by force what can be won by deception")))
  (is (= ["Never attempt to win by force "
          "e what can be won by deception"
          "on"]
         (split/chunk-text
          {split/chunk-size 30 split/overlap 2 split/split-unit split/character}
          "Never attempt to win by force what can be won by deception"))))

(deftest splitting-by-sentence
  (let [text (str
              "Jenny lost keys. Panic rises. Frantic search begins." " "
              "Couch cushions invaded. Discovery: in pocket.")]
    (is (= ["Jenny lost keys. Panic rises. Frantic search begins."
            "Couch cushions invaded. Discovery: in pocket."]
           (split/chunk-text {split/chunk-size 3 split/split-unit split/sentence} text)))
    (is (= ["Jenny lost keys. Panic rises. Frantic search begins."
            "Frantic search begins. Couch cushions invaded. Discovery: in pocket."
            "Discovery: in pocket."]
           (split/chunk-text
            {split/chunk-size 3 split/overlap 1 split/split-unit split/sentence}
            text)))
    (is (= ["Jenny lost keys. Panic rises. Frantic search begins. Couch cushions invaded. Discovery: in pocket."]
           (split/chunk-text {split/chunk-size 30 split/split-unit split/sentence} text)))))
