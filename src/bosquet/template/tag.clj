(ns bosquet.template.tag
  (:require
   [bosquet.complete :as complete]
   [bosquet.llm.llm :as llm]
   [clojure.string :as string]
   [selmer.parser :as parser]))

(def ^:private preceding-text
  "This is where Selmer places text preceding the `gen` tag"
  :selmer/preceding-text)

(defn args->map
  "Convert tag arguments to a clojure map. Tag arguments are passed in
  as a vector of 'key=value' strings."
  [args]
  (reduce (fn [m arg]
            (let [[k v] (string/split arg #"=")]
              (assoc m (keyword k) v)))
          {} args))

(defn gen-tag
  "Selmer custom tag to invoke AI generation"
  [args {prompt preceding-text
         :as    opts}]
  (let [result (complete/complete
                prompt
                 ;; whatever props are specified in the props will take priority
                 ;; over the ones specified in the tag
                 ;; FIXME the merge does not merge at the `llm-config` level
                opts #_(merge (args->map args) opts))]
    (-> result llm/content :completion)))

(defn add-tags []
  (parser/add-tag! :gen gen-tag)
  ;; for backwards compatability
  (parser/add-tag! :llm-generate gen-tag))
