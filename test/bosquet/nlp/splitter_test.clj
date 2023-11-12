(ns bosquet.nlp.splitter-test
  (:require
   [bosquet.llm.openai-tokens :as oai]
   [bosquet.nlp.splitter :as sub]
   [clojure.test :refer [deftest is]]))

(deftest max-tokens-under
  ;; make sure than no chunk is larger than MAX tokens
  (let [MAX 4 model "gpt-4"]
    (is (> MAX
           (apply max
                  (->>
                   (sub/split-max-tokens
                    "Talks from the AI Engineer Summit, October 8-10 2023"
                    MAX model)
                   (map #(count (oai/encode % model)))))))))

(deftest character-splitter-test
  (is (= ["Never attempt to win by force "
          "what can be won by deception"]
         (sub/text-chunker
          {:chunk-size 30 :splitter sub/character-splitter}
          "Never attempt to win by force what can be won by deception")))
  #_(is (= ["Never attempt to win by forc"
            "e what can be won by deception"]
           (sub/character-splitter
            {:chunk-size 30 :overlap 2 :splitter sub/character-splitter}
            "Never attempt to win by force what can be won by deception"))))

(deftest splitting-by-sentence
  (let [text (str
              "Jenny lost keys. Panic rises. Frantic search begins." " "
              "Couch cushions invaded. Discovery: in pocket.")]
    (is (= ["Jenny lost keys. Panic rises. Frantic search begins."
            "Couch cushions invaded. Discovery: in pocket."]
           (sub/text-chunker {:chunk-size 3 :splitter sub/sentence-splitter} text)))
    #_(is (= ["Jenny lost keys. Panic rises. Frantic search begins."
              "Frantic search begins. Couch cushions invaded. Discovery: in pocket."]
             (sub/text-chunker {:chunk-size 3 :overlap 1 :splitter sub/sentence-splitter} text)))
    (is (= ["Jenny lost keys. Panic rises. Frantic search begins. Couch cushions invaded. Discovery: in pocket."]
           (sub/text-chunker {:chunk-size 30 :splitter sub/sentence-splitter} text)))))
