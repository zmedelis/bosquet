(ns bosquet.lab-v2
  (:require
    [bosquet.template :as template]
    [bosquet.template.tag :as tag]
    [clojure.java.io :as io]
    [selmer.parser :as parser]))

(defn prompt-def []
  (template/read-edn (io/reader "resources/pp2.edn")))

(tag/add-tags)

(comment
  (def s (prompt-def))
  (parser/render-with-values (:text-analyzer/summarize s)
    {:text "A very long text to be summarized."
     :text-type "sentence"}))
