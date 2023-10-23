(ns bosquet.complete-test
  (:require
   [clojure.test :refer [deftest is]]
   [bosquet.complete :refer [generate-with-cache evict]]))

(deftest bosquet.complete-test
  (let [call-counter (atom 0)
        generator    (fn [_prompt _params] (swap! call-counter inc))
        prompt       "2 + 2 ="]
    ;; cache is off
    (reset! call-counter 0)
    (generate-with-cache false generator prompt {})
    (is (= 1 @call-counter))
    (generate-with-cache false generator prompt {})
    (is (= 2 @call-counter))
    ;; cache is on
    (evict prompt {})
    (reset! call-counter 0)
    (generate-with-cache true generator prompt {})
    (is (= 1 @call-counter))
    (generate-with-cache true generator prompt {})
    (is (= 1 @call-counter))))
