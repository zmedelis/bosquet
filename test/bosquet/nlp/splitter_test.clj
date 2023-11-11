(ns bosquet.nlp.splitter-test
  (:require
   [bosquet.llm.openai-tokens :as oai]
   [bosquet.nlp.splittertter :as splitter]
   [clojure.test :refer [deftest is]]))

(deftest max-tokens-under
  ;; make sure than no chunk is larger than MAX tokens
  (let [MAX 4 model "gpt-4"]
    (is (> MAX
           (apply max
                  (->>
                   (splitter/split-max-tokens
                    "Talks from the AI Engineer Summit, October 8-10 2023"
                    MAX model)
                   (map #(count (oai/encode % model)))))))))
