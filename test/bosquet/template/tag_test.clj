(ns bosquet.template.tag-test
  (:require
   [bosquet.template.tag :as tag]
   [bosquet.wkk :as wkk]
   [clojure.test :refer [deftest is use-fixtures]]
   [selmer.parser :as parser]))

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

(deftest arg-use-and-priority
  (is (= nil (tag/generation-params nil nil)))

  (is (= {wkk/gen-var-name wkk/default-gen-var-name
          wkk/llm-config   {wkk/default-gen-var-name {:x "1" :y "2"}}}
         (tag/generation-params ["x=1" "y=2"] nil)))

  (is (= {wkk/gen-var-name wkk/default-gen-var-name
          wkk/llm-config   {wkk/default-gen-var-name {:x "1" :y "2"}}}
         (tag/generation-params ["x=1" "y=2"] nil)))

  (is (= {wkk/gen-var-name :test1
          wkk/llm-config   {:test1 {:x "override" :y "2"}}}
         (tag/generation-params ["var=test1" "x=1" "y=2"] {wkk/llm-config {:test1 {:x "override"}}}))))
