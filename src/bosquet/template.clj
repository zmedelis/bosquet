(ns bosquet.template
  (:require
    [selmer.parser :as selmer]
    [selmer.util :refer [without-escaping]]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as string]))

(defn load-edn [file]
  (with-open [rdr (io/reader file)]
    (edn/read (java.io.PushbackReader. rdr))))

(defn- edn-file? [file] (string/ends-with? (.getName file) ".edn"))

(defn load-palettes
  "Build a map of all the prompt palletes defined in `dir`.
  It will read all EDN files in that dir and construct mapping
  where key is file name and content is patterns defined in that file."
  [dir]
  (->> (io/file dir)
    (file-seq)
    (filter edn-file?)
    (reduce
      (fn [m file] (merge m (load-edn file)))
      {})))

(defn slots-required
  "Find slots reffered to in the template"
  [text]
  (selmer/known-variables text))

(defn missing-value-fn
  "If the value is missing do not discard slot placeholder.
  It will be filled by generator"
  [tag _context-map]
  (str "{{" (:tag-value tag) "}}"))

(selmer.util/set-missing-value-formatter! missing-value-fn)

(defn fill-slots
  "Use Selmer to fill in `text` template `slots`"
  [text slots]
  (without-escaping
    (selmer/render text slots)))
