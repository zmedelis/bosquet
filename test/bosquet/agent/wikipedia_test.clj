(ns bosquet.agent.wikipedia-test
  (:require
    [clojure.test :refer [deftest is]]
    [bosquet.agent.wikipedia :as w]))


(def search-result
  [["Fox" "https://en.wikipedia.org/wiki/Fox"]
           ["Fox News" "https://en.wikipedia.org/wiki/Fox_News"]
           ["Fox Broadcasting Company" "https://en.wikipedia.org/wiki/Fox_Broadcasting_Company"]])

(deftest best-match-test
  (is (= ["Fox" "https://en.wikipedia.org/wiki/Fox"]
        (w/best-match "Fox" search-result)))
  (is (= ["Fox" "https://en.wikipedia.org/wiki/Fox"]
        (w/best-match "Box" search-result)))
  (is (nil? (w/best-match "Box" []))))
