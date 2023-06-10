(ns bosquet.agent.wikipedia-test
  (:require
    [bosquet.agent.agent :as a]
    [bosquet.agent.wikipedia :as w]
    [clojure.test :refer [deftest is]])
  (:import
    [bosquet.agent.wikipedia Wikipedia]))

(def ^:private fox-result
  ["Fox" "Fox News" "Fox Broadcasting Company"])

(deftest best-match-test
  (is (= "Fox" (w/best-match "Fox" fox-result)))
  (is (= "Fox" (w/best-match "Box" fox-result)))
  (is (nil? (w/best-match "Box" []))))
