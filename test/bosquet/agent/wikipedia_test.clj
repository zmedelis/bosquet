(ns bosquet.agent.wikipedia-test
  (:require
    [clojure.test :refer [deftest is]]
    [bosquet.agent.wikipedia :as w]))

(def fox-result
  ["Fox" "Fox News" "Fox Broadcasting Company"])

(deftest best-match-test
  (is (= "Fox" (w/best-match "Fox" fox-result)))
  (is (= "Fox" (w/best-match "Box" fox-result)))
  (is (nil? (w/best-match "Box" []))))
