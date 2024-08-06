(ns bosquet.llm.gen-data
  "All things concerning generation data:
  * converting inputs and outputs
  * usage tokens calculations
  * etc")

(defn total-usage
  "Calculate aggreage token usage across all ai generation nodes"
  [usages]
  (reduce-kv
   (fn [{:keys [prompt completion total]
         :or  {prompt 0 completion 0 total 0}
         :as  aggr}
        _k
        {p :prompt c :completion t :total}]
     (assoc aggr
            :prompt (+ (or p 0) prompt)
            :completion (+ (or c 0) completion)
            :total (+ (or t 0) total)))
   {}
   usages))

(defn reduce-gen-graph
  "Reduce generation prompt map, where a node is representing llm generation spec
  call `gen-node-fn`"
  [gen-node-fn prompt-map]
  (reduce-kv
   (fn [m k v]
     (if (map? v) (gen-node-fn m k v) m))
   {} prompt-map))
