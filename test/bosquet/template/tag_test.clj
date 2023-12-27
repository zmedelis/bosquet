(ns bosquet.template.tag-test
  (:require
   [bosquet.template.tag :as tag]
   [clojure.test :refer [deftest is use-fixtures]]
   [selmer.parser :as parser]))

(defn tag-fixture [f]
  (with-redefs [tag/gen-tag  (fn [_args _context] "GEN")]
    (tag/add-tags)
    (f))
  ;; restore original tag setup
  (tag/add-tags))

(use-fixtures :once tag-fixture)

(deftest gen-tag
  (is (= "val = GEN" (parser/render "{{x}} = {% gen my-var %}" {:x "val"}))))
