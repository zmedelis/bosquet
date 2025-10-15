(ns bosquet.utils-test
  (:require [bosquet.utils :as u]
            [clojure.test :refer [deftest is]]))

(deftest snake-case-conversions
  (is (= {:fox_box 1 :box_fox 2 :box 3}
         (u/snake-case {:fox-box 1 :boxFox 2 :BOX 3})))
  (is (= {:fox_box {:bird_grid 1}}
         (u/snake-case {:fox-box {:birdGrid 1}}))))
