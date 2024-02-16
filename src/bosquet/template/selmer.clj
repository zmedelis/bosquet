(ns bosquet.template.selmer
  (:require
   [clojure.set :as set]
   [clojure.string :as string]
   [selmer.filter-parser :refer [literal? split-value]]
   [selmer.parser :as selmer]
   [selmer.tags :as tag]
   [selmer.util :as util]))

;;
;; Copy/Paste from Selmer
;; Enables `known-variables-in-order`
;;

(defn- parse-variable-paths
  "
  takes in vals like: \"person.name|capitalize\"
  and produces [:person :name]
  "
  [arg]
  (some-> arg split-value first util/parse-accessor))

(defn ^:private parse-variables [tags]
  (loop [vars        []                 ; Selmer uses 'set'
         nested-keys []                 ; Selmer uses 'set'
         tags        tags]
    (if-let [{:keys [tag-type tag-name tag-value args]} (first tags)]
      (cond
        (= :filter tag-type) (let [v               (parse-variable-paths tag-value)
                                   should-add-var? (when (vector? v)
                                                     (not (contains? nested-keys (first v))))
                                   updated-vars    (cond-> vars
                                                     should-add-var? (conj v))]
                               (recur
                                updated-vars
                                nested-keys
                                (rest tags)))
        (= :for tag-name)    (let [[ids [_ items]] (tag/aggregate-args args)]
                               (recur
                                (conj vars (parse-variable-paths items))
                                (conj (set (map keyword ids)) :forloop)
                                (rest tags)))

        (= :with tag-name) (let [[id value] (string/split (first args) #"=")]
                             (recur
                              (conj vars (parse-variable-paths value))
                              #{(keyword id)}
                              (rest tags)))

        (contains? #{:endfor :endwith} tag-name) (recur vars #{} (rest tags))

        :else
        (let [special-syms   #{nil :not :all :any :< :> := :<= :>=}
              should-remove? (fn [[var-head]]
                               (or
                                (special-syms var-head)
                                (nested-keys  var-head)))]
          (recur (set/union
                  vars
                  (->> args
                       (filter (complement literal?))
                       (map parse-variable-paths)
                       (remove should-remove?)
                       set))
                 nested-keys
                 (rest tags))))
      vars)))

;; ---

(defn known-variables-in-order
  "The same as Selmer's `known-variables` but do not produce
  set thus loosing the order"
  [input & [opts]]
  (->> (or opts {})
       (selmer/parse selmer/parse-input (java.io.StringReader. input))
       meta
       :all-tags
       parse-variables
       (map first)))

(defn known-variables
  "Known variables in the `template` or templates"
  [template]
  (let [xf (comp
            (remove nil?)
            (mapcat (fn [template]
                      (when-not (map? template)
                        (selmer/known-variables template)))))]
    (into #{} xf (if (string? template) [template] template))))

(defn render [text ctx]
  (when-not (string/blank? text)
    (util/without-escaping
      (selmer/render text ctx))))

(defn missing-value-noop [tag _context-map]
  (format "{{%s}}" (:tag-value tag)))

(defn set-missing-value-formatter
  "Since some of the slots are AI-generated later in the process,
  do not touch slots that have no date in parsing context."
  []
  (util/set-missing-value-formatter! missing-value-noop))

(defn- kw->str
  [kw]
  (let [ns        (namespace kw)
        name      (name kw)]
    (string/replace
     (if ns (str ns "/" name) name)
     ;; ecape '.' for Selmer
     "." "..")))

(defn clear-gen-var-slot
  "Remove a `slot` reference from the `template` and
  all the text after it. This is to enforce the generation
  context up to the generation slot.

  `template` = '{{x}}^2 = {{y}} further text'
  `slot` = '{{y}}'
  => '{{x}}^2 = '"
  [template slot]
  (string/replace
   template
   (->> slot kw->str (format "\\{\\{%s\\}\\}.*") re-pattern)
   ""))

(defn append-slot
  [template slot]
  (format "%s {{%s}}" template (kw->str slot)))
