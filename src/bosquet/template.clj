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

(defn- file-name->kw [file]
  (-> (.getName file)
    (string/replace ".edn" "")
    keyword))

(defn load-palettes
  "Build a map of all the prompt palletes defined in `dir`.
  It will read all EDN files in that dir and construct mapping
  where key is file name and content is patterns defined in that file."
  [dir]
  (->> (io/file dir)
    (file-seq)
    (filter edn-file?)
    (reduce
      (fn [m file]
        (merge m (load-edn file))
        #_(assoc m (file-name->kw file) (load-edn file)))
      {})))

(defn slots-required
  "Find slots reffered to in the template"
  [text]
  (mapv (comp keyword second)
    (re-seq #"\{\{(.*?)(\|.*?)?\}\}"
      ;; As per Selmers doc https://github.com/yogthos/Selmer#namespaced-keys ;;
      ;; 'Note that if you're using namespaced keys, such as :foo.bar/baz,
      ;; then you will need to escape the .'
      (string/replace text #"\.\." "."))))

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
