(ns bosquet.template.tag-test
  (:require
   [clojure.test :refer [deftest is]]
   [bosquet.template.tag :refer [args->map]]))

(deftest args->map-test
  (is (= (args->map ["x=1" "y=2"]) {:x "1" :y "2"})))
