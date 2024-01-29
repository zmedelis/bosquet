(ns bosquet.llm.gen-tree
  (:require
   [clojure.string :as string]))

(defn- escape-name
  "If a `var-name` is a simple keyword, then to string
  If a `var-name` is a fully qualified then escape `/`"
  [var-name]
  (if-let [ns (namespace var-name)]
    (format "%s__%s" ns (name var-name))
    (name var-name)))

(defn- unescape-name
  [var-name]
  (let [ns (namespace var-name)
        n  (name var-name)]
    (if (= "bosquet.depseq" ns)
      (keyword (-> n (string/replace #"__" "/")))
      var-name)))

(defn- ref-name
  [var-name part-nr]
  (format "{{bosquet..depseq/%s-%s}}" (escape-name var-name) part-nr))

(defn- ref-kw
  [var-name part-nr]
  (keyword (format "bosquet.depseq/%s-%s" (escape-name var-name) part-nr)))

(defn partition-template
  [template]
  (if (string? template)
    (let [slots (mapv first (re-seq #"(?sm)(.*?)(\{\{.*?\}\})" template))
          ;; slots (remove (partial dependency-slot? template-map) slots)
          ;; will attach the remainder of the template text
          tail (string/replace template (string/join slots) "")]
      (if (string/blank? tail)
        slots
        (conj slots tail)))
    template))

(defn depend-tree
  [var-name template]
  (reduce (fn [m [idx part]]
            (assoc
             m
             (ref-kw var-name idx)
             (if (zero? idx)
               part
               (str (ref-name var-name (dec idx)) part))))
          {}
          (map-indexed vector
                       (partition-template template))))

(defn expand-dependencies
  [template-map]
  (reduce-kv (fn [dep-tree var-name template]
               (merge
                dep-tree
                (if (string? template)
                  (depend-tree var-name template)
                  {var-name template})))
             {}
             template-map))

(defn- drop-seq-number
  [kw]
  (-> (unescape-name kw) str (subs 1) (string/replace #"-\d+$" "") keyword))

(defn collapse-resolved-tree
  "Restore the original context tree collapsing created dependency tree"
  [resolved-tree]
  (->> resolved-tree
       keys
       (group-by #(-> % name
                      ;; group by var name minus dependency number
                      (string/replace #"-\d+$" "")))
       (map (fn [[_k v]]
              (last (sort v))))
       (select-keys resolved-tree)
       (reduce-kv (fn [m k v]
                    (assoc m (drop-seq-number k) v)) {})))

(comment
  (def in {:tasks "First, I am doing {{A}} followed by {{B}} task."
           :A     "gen-A"
           :B     "gen-B"})

  (collapse-resolved-tree
   {:bosquet.depseq/today__tasks-0 "A"
    :bosquet.depseq/today__tasks-1 "A B"
    :bosquet.depseq/today__tasks-2 "A B C"
    :A                             {:llm :agi}
    :bosquet.depseq/log-0          "1"
    :bosquet.depseq/log-1          "1 2"})

  (depend-tree
   "tasks"
   (partition-template (:tasks in)))
  #__)
