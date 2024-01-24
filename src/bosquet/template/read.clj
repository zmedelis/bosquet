(ns bosquet.template.read
  (:require
   [bosquet.utils :as u]
   [bosquet.wkk :as wkk]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.string :as string]
   [selmer.parser :as selmer]
   [selmer.util :refer [without-escaping]]
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

(def ^:private ^{:deprecated true} var-name ":var-name=")
(def ^:prvate var-name2 ":var=")

(def gen-tag-name "gen")

(defn has-gen-tag? [template]
  (re-find
   (re-pattern (str "\\{%\\s+" gen-tag-name "\\s+\\S*?\\s*%\\}"))
   template))

(defn ensure-gen-tag
  [template]
  (if (has-gen-tag? template)
    template
    (format "%s {%% %s %%}" template gen-tag-name)))

(defn- var-name? [name]
  (or
   (string/starts-with? (str name) var-name)
   (string/starts-with? (str name) var-name2)))

(defn generation-vars [template]
  ;; FIXME. This is bad. `known-variables` will return all vars not just `gen`
  ;; need filter only by gen tag, then allow for gens that do not specify
  ;; var-name, in that case autogenerate something like
  ;; `key-gen<index>`
  (->> (selmer/known-variables template)
       (filter var-name?)
       (map (fn [variable]
              (keyword (string/replace-first
                        (str variable) #".*=" ""))))
       (set)))

(defn generation-vars2 [template]
  (set (map
        (fn [[_ var]]
          (if (string/blank? var)
            (keyword "bosquet" "gen")
            (keyword var)))
        (re-seq
         (re-pattern (str "\\{%\\s*" gen-tag-name "\\s*(.*?)\\s*%\\}")) template))))

(defn template-vars
  "Extract variables from the `tempalte`. There are two types of variables:

  - `data-vars` are your regular template variables like `{{x}}` those come from
    the passed in data or template references

  - `gen-vars` are generation variables declared in `gen` tag and those will hold
  generation results."
  [template]
  (let [all-vars (selmer/known-variables template)
        gen-vars (generation-vars2 template)]
    {:data-vars (vec (set/difference all-vars gen-vars))
     :gen-vars  (vec gen-vars)}))

(defn slots-required
  "Find slots reffered to in the template"
  [text]
  (set
   (remove
    ;; remove config values coming from tags like `gen`
    (fn [variable] (string/includes? (name variable) "="))
    (selmer/known-variables text))))

(defn fill-slots
  "Use Selmer to fill in `text` template `slots`"
  ([text ctx] (fill-slots text ctx nil))
  ([text ctx config]
   (without-escaping
    (selmer/render-with-values
     text
     (assoc ctx wkk/llm-config config)))))
