(ns bosquet.template.tag-test
  (:require
   [selmer.parser :as parser]
   [clojure.test :refer [deftest is use-fixtures]]
   [bosquet.template.tag :as tag]))

(defn tag-fixture [f]
  (with-redefs [tag/gen-tag (fn [_args _context] "GEN")]
    (tag/add-tags)
    (f))
  ;; restore original tag setup
  (tag/add-tags))

(use-fixtures :once tag-fixture)

(deftest gen-tag-parsing
  (is (= "val = GEN" (parser/render "{{x}} = {% gen %}" {:x "val"})))
  (is (= "val = GEN" (parser/render "{{x}} = {% llm-generate %}" {:x "val"}))))

(deftest args->map-test
  (is (= (tag/args->map ["x=1" "y=2"]) {:x "1" :y "2"})))
