(ns bosquet.splitter-test
  (:require
     [clojure.test :refer [deftest is testing]]
     [bosquet.splitter :as splitter]))

(deftest max-tokens-under
  (is (= [2 2 1]
         (->>
          (splitter/split-max-tokens "this is a teeeeeeexxxxxxxxxt" 3
                                     splitter/heuristic-token-count-fn "")
          (map splitter/heuristic-token-count-fn)))))

(deftest max-tokens-over
  (is (= [2 4]
         (->>
          (splitter/split-max-tokens "this is a teeeeeeexxxxxxxxxt" 3
                                     splitter/heuristic-token-count-fn)
          (map splitter/heuristic-token-count-fn)))))
