(ns bosquet.complete-test
  (:require
   [clojure.test :refer [deftest is]]
   [bosquet.complete :as sub]))

(deftest bosquet.complete-test
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

(deftest available-memories-test
  (is (= [:message1 :message2]
        ;; memory is not configured, return existing messages as is
         (sub/available-memories [:message1 :message2] nil))))
