(ns bosquet.llm.openai-tokens-test
  (:require
   [clojure.test :refer [deftest is]]
   [bosquet.llm.openai-tokens :as tok]))

(deftest encoding-decoding
  (let [txt    "A screaming comes across the sky."
        tokens (tok/encode txt :gpt-3.5-turbo)]
    (is (= txt (tok/decode tokens :gpt-3.5-turbo)))))

(deftest price-estimation
  (is (= (+ (* 7 0.003))
         (tok/generation-price-estimate
          "A screaming comes across the sky."
          :gpt-4)))
  (is (= (+ (* 7 0.003) (* 15 0.006))
         (tok/generation-price-estimate
          "A screaming comes across the sky."
          "It has happened before, but there is nothing to compare it to now."
          :gpt-4)))
  (is (= (+ (* 10 0.003) (* 20 0.006))
         (tok/generation-price-estimate 10 20 :gpt-4)))
  (is (= (* 0.0001 1000) (tok/embeddings-price-estimate
                           ;; make 1k tokens; 'abc' = 1tok
                          (apply str (take 1000 (repeat "abc"))))))
  (is (= (* 0.0001 1000) (tok/embeddings-price-estimate 1000))))

(deftest max-tokens
  (is (tok/fits-in-context-window? 1 :text-babbage-002))
  (is (tok/fits-in-context-window? 2049 :text-babbage-002))
  (is (not (tok/fits-in-context-window? 20000 :text-babbage-002))))
