(ns bosquet.complete-test
  (:require
   [clojure.test :refer [deftest is]]
   [bosquet.complete :as sub]))

(deftest chache-usage
  (let [call-counter (atom 0)
        generator    (fn [_prompt _params] (swap! call-counter inc))
        prompt       "2 + 2 ="]
    ;; cache is off
    (reset! call-counter 0)
    (sub/generate-with-cache false generator prompt {})
    (is (= 1 @call-counter))
    (sub/generate-with-cache false generator prompt {})
    (is (= 2 @call-counter))
    ;; cache is on
    (sub/evict prompt {})
    (reset! call-counter 0)
    (sub/generate-with-cache true generator prompt {})
    (is (= 1 @call-counter))
    (sub/generate-with-cache true generator prompt {})
    (is (= 1 @call-counter))))
