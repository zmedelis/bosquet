(ns bosquet.openai.tokens-test
  (:require
   [clojure.test :refer [deftest is]]
   [bosquet.openai.tokens :refer [price-estimate encode decode fits-in-context-window?]]))


(deftest encoding-decoding
  (let [txt    "A screaming comes across the sky."
        tokens (encode txt :gpt-3.5-turbo)]
    (is (= txt (decode tokens :gpt-3.5-turbo)))))

(deftest price-estimation
  (is (= (+ (* 7 0.003))
        (price-estimate
          "A screaming comes across the sky."
          :gpt-4)))
  (is (= (+ (* 7 0.003) (* 15 0.006))
        (price-estimate
          "A screaming comes across the sky."
          "It has happened before, but there is nothing to compare it to now."
          :gpt-4))))

(deftest max-tokens
  (is (fits-in-context-window? 1 :text-ada-001))
  (is (fits-in-context-window? 2049 :text-ada-001))
  (is (not (fits-in-context-window? 10000 :text-ada-001))))
