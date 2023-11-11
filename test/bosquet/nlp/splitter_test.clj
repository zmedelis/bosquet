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
         (sub/character-splitter
          "Never attempt to win by force what can be won by deception"
          {:chunk-size 30})))
  (is (= ["Never attempt to win by forc"
          "e what can be won by deception"]
         (sub/character-splitter
          "Never attempt to win by force what can be won by deception"
          {:chunk-size 30
           :overlap    2}))))
