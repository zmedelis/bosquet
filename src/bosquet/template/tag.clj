(ns bosquet.template.tag
  (:require
   [bosquet.complete :as complete]
   [clojure.string :as string]
   [selmer.parser :as parser]))

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
  [args context]
  (complete/complete
    (:selmer/preceding-text context)
    (args->map args)))

(defn add-tags []
  (parser/add-tag! :gen gen-tag)
  ;; for backwards compatability
  (parser/add-tag! :llm-generate gen-tag))
