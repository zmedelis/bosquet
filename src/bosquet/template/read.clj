(ns bosquet.template.read
  (:require
   [bosquet.utils :as u]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [taoensso.timbre :as timbre]))

(defn read-edn [reader]
  (edn/read (java.io.PushbackReader. reader)))

(defn load-prompt-palette-edn [file]
  (timbre/info "Read prompts from: " (.getName file))
  (with-open [rdr (io/reader file)]
    (reduce-kv (fn [m k v] (assoc m k
                                  (if (sequential? v) (u/join-coll v) v)))
               {} (read-edn rdr))))

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
        (fn [m file] (merge m (load-prompt-palette-edn file)))
        {})))
