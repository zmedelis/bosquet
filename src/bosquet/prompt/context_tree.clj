(ns bosquet.prompt.context-tree
  (:require
   [clojure.string :as string]
   [com.wsscode.pathom3.connect.indexes :as pci]
   [com.wsscode.pathom3.connect.operation :as pco]
   [com.wsscode.pathom3.interface.eql :as p.eql]
   [ubergraph.core :as uber]))

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
    (if (= "bosquet.context-tree" ns)
      (keyword (-> n (string/replace #"__" "/")))
      var-name)))

(defn- ref-name
  [var-name part-nr]
  (format "{{bosquet..context-tree/%s-%s}}" (escape-name var-name) part-nr))

(defn- ref-kw
  [var-name part-nr]
  (keyword (format "bosquet.context-tree/%s-%s" (escape-name var-name) part-nr)))

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


(defn prompts->context-tree
  [prompts]
  )


;; ----

(defn node
  ([id] id)
  ([id content] [id {:content content}]))

(defn chain-nodes [nodes]
  (mapv vec
        (mapcat (fn [a b] [a b])
                (partition-all 2 (butlast nodes))
                (partition-all 2 (drop 1 nodes)))))

(defn root-nodes [graph]
  (let [all-nodes             (uber/nodes graph)
        nodes-with-successors (set (mapcat (partial uber/successors graph) all-nodes))]
    (set (remove nodes-with-successors all-nodes))))

(defn terminal-nodes [graph]
  (->> graph
       uber/nodes
       (filter (fn [node] (zero? (uber/out-degree graph node))))
       set))

(defn- ->resolver
  [id input output]
  (let [in-id (if (sequential? input) (first input) input)
        out-id (if (sequential? output) (first output) output)]
    (prn "INPUT" in-id "OUTPUT" out-id)
    (pco/resolver
     {::pco/op-name (symbol (str "id" id))
      ::pco/output  [out-id]
      ::pco/input   [in-id]
      ::pco/resolve (fn [_env _in]
                      {out-id [in-id out-id]})})))

(defn- resolvers [prompt-tree]
  (pci/register
   (vec
    (map-indexed
     (fn [id [from to]] (->resolver id from to))
     prompt-tree))))

(defn calc
  [env data retrieval-ids]
  (p.eql/process env data retrieval-ids))

(comment
  (def in {:tasks "First, I am doing {{A}} followed by {{B}} task."
           :nextA "{{tasks}} Once {{A}} and {{B}} are finished start {{C}}"
           :nextB "{{tasks}} In parallel request {{D}}"
           :A     {:llm :A}
           :B     {:llm :B}
           :C     {:llm :C}
           :D     {:fun :D}})

  (def n {:1  (node :tasks)
          :2  (node :tasks1 "First, I am doing ")
          :3  (node :tasks2 :A)
          :4  (node :tasks3 " followed by ")
          :5  (node :tasks4 :B)
          :6  (node :tasks5 " task.")
          :7  (node :nextA)
          :8  (node :nextA1 " Once ")
          :9  (node :nextA2 :A)
          :10 (node :nextA3 " and ")
          :11 (node :nextA4 :B)
          :12 (node :nextA5 " are finished start ")
          :13 (node :nextA6 :C)
          :14 (node :nextB)
          :15 (node :nextB1 " IN parallel request ")
          :16 (node :nextB1 :D)})

  (def prompt-tree
    [[(:1 n) (:2 n)]
     [(:2 n) (:3 n)]
     [(:3 n) (:4 n)]
     [(:4 n) (:5 n)]
     [(:5 n) (:6 n)]
     ;; end tasks
     [(:6 n) (:7 n)]
     [(:7 n) (:8 n)]
     [(:8 n) (:9 n)]
     [(:9 n) (:10 n)]
     [(:10 n) (:11 n)]
     [(:11 n) (:12 n)]
     [(:12 n) (:13 n)]
     ;; end nexta
     [(:6 n) (:14 n)]
     ])

  (def gg (apply uber/digraph prompt-tree))

  (def env (resolvers prompt-tree))
  (calc env
        {:tasks :xxx}
        [:nextA5 :nextB1])

  (def exp (expand-dependencies in))
  (def g (apply uber/digraph (into [] (chain-nodes exp))))

  (root-nodes gg)
  (terminal-nodes gg)
  (uber/pprint g)
  (uber/viz-graph gg)

  (collapse-resolved-tree
   {:bosquet.context-tree/today__tasks-0 "A"
    :bosquet.context-tree/today__tasks-1 "A B"
    :bosquet.context-tree/today__tasks-2 "A B C"
    :A                             {:llm :agi}
    :bosquet.context-tree/log-0          "1"
    :bosquet.context-tree/log-1          "1 2"})

  (partition-template "ab {{x}} = {{y}} oo")

  (depend-tree
   :tasks
   (partition-template (:tasks in)))
  #__)


(comment

  (def in {:tasks "First, I am doing {{A}} followed by {{B}} task."
           :nextA "{{tasks}} Once {{A}} and {{B}} are finished start {{C}}"
           :nextB "{{tasks}} In parallel request {{D}}"
           :summary "With {{nextA}} and {{nextB}} done this is how it went {{SUM}}"
           :A     {:llm :A}
           :B     {:llm :B}
           :C     {:llm :C}
           :D     {:fun :D}
           :SUM   {:llm :X}})


  (def path1
    (str
"First, I am doing {{A}} followed by {{B}} task."
     )

    )

  )
