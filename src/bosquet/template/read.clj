(ns bosquet.template.read
  (:require
   [bosquet.llm.wkk :as wkk]
   [bosquet.template.selmer :as selmer]
   [bosquet.utils :as u]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.set :as set]
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

(defn data-slots
  "Extract data slots that are defined in the template, chat, or graph context.
  This will ignore all the self references and generation slots,
  only return slots that are suplied as data and not defined in prompts."
  [tpl-chat-or-graph]
  ;; Different processing is needed for map based graph prompts
  ;; and chats
  (let [templates     (cond
                        (string? tpl-chat-or-graph) [tpl-chat-or-graph]
                        (map? tpl-chat-or-graph)    (->> tpl-chat-or-graph vals (map u/join-coll))
                        (vector? tpl-chat-or-graph) (map (fn [[_ content]]
                                                           (u/join-coll content)) tpl-chat-or-graph))
        non-data-refs (set (cond
                             (map? tpl-chat-or-graph)    (keys tpl-chat-or-graph)
                             (vector? tpl-chat-or-graph) (map (fn [[_ content]]
                                                                (when (map? content) (wkk/var-name content)))
                                                              tpl-chat-or-graph)))
        slots         (selmer/known-variables templates)]
    (set/difference slots non-data-refs)))
