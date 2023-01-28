(ns bosquet.lab-v2
  (:require
    [bosquet.template :as template]
    [clojure.java.io :as io]
    [selmer.parser :as parser]
    [selmer.util :as u]))


(defn prompt-def []
  (template/read-edn (io/reader "resources/pp2.edn"))
  )


(defmacro add-tag!
  " tag name, fn handler, and maybe tags "
  [k handler & tags]
  `(do
     (parser/set-closing-tags! ~k ~@tags)
     (swap! selmer.tags/expr-tags assoc ~k (tag-handler ~handler ~k ~@tags))))

(parser/add-tag! :foo
  (fn [args context-map]
    (prn "ARGS " args)
    (prn "CM   " context-map)
    (str "foo " (first args))))


(defn render-template
  " vector of ^selmer.node.INodes and a context map."
  [template context-map]
  (let [buf (StringBuilder.)]
    (doseq [^selmer.node.INode element template]
      #_(prn element ":" (.render-node element context-map))
      (if-let [value (.render-node element context-map)]
        (.append buf value)
        (.append buf (u/*missing-value-formatter* (:tag (meta element)) context-map))))
    (.toString buf)))

(defn render
  " render takes the string, the context-map and possibly also opts. "
  [s context-map & [opts]]
  (let [p (parser/parse parser/parse-input
            (java.io.StringReader. s) opts)]
    (prn "META " (meta p))
    (prn "P    " (map meta p))
    (render-template p context-map)))


(comment
  (def s "fff {{a}} {{b}}")
  (render s {:a 100 :b 'foo}
    #_{:tag-open (fn [x] (prn "XXX" x) x)}
    )

  )
