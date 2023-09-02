(ns bosquet.template.read
  (:require
   [bosquet.system :as system]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [selmer.parser :as selmer]
   [selmer.util :refer [without-escaping]]
   [taoensso.timbre :as timbre]))

(defn read-edn [reader]
  (edn/read (java.io.PushbackReader. reader)))

(defn load-prompt-palette-edn [file]
  (timbre/info "Read prompts from: " (.getName file))
  (with-open [rdr (io/reader file)]
    (reduce-kv (fn [m k v] (assoc m k v))
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

(def ^:private var-name ":var-name=")

(defn generation-vars [template]
  (->> (selmer/known-variables template)
       (filter (fn [variable]
                 (string/starts-with? (str variable) var-name)))
       (map (fn [variable]
              (keyword (string/replace-first
                        (str variable) var-name ""))))
       (set)))

(defn slots-required
  "Find slots reffered to in the template"
  [text]
  (set
   (remove
      ;; remove config values coming from tags like `llm-generate`
    (fn [variable] (string/includes? (name variable) "="))
    (selmer/known-variables text))))

(defn fill-slots
  "Use Selmer to fill in `text` template `slots`"
  [text ctx system]
  (without-escaping
   (selmer/render-with-values text
                              (assoc ctx system/llm-service-key system))))
