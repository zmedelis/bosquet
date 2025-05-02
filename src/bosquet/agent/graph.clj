(ns bosquet.agent.graph
  (:require [loom.attr :as attr]
            [loom.graph :as lg]
            [loom.io :as lio]
            [bosquet.llm.generator :as g]))

(defmacro defgraph
 "
🧠 Create a loom digraph that can be run within the agent (edges + optional labels)
 "
  [name & edge-defs]
  `(def ~name
     (let [g0# (lg/digraph)]
       (reduce
         (fn [ret# [from# to# & [attrs#]]]
           (if (map? to#) ;conditional edge
             (-> (assoc ret# :graph 
                        (reduce 
                          (fn [g# [cond# to0#]] 
                            (-> g# 
                                (lg/add-nodes from# to0#)
                                (lg/add-edges [from# to0#])
                                (attr/add-attr from# to0# :label (str cond#)) ))
                          (:graph ret#) to#)) 
                 (assoc-in [:condition-fns from#] attrs#)
                 (assoc-in [:condition-maps from#] to#))
             (assoc ret# :graph
                    (-> (:graph ret#) 
                        (lg/add-nodes from# to#)
                        (lg/add-edges [from# to#])))))
           {:graph g0# :condition-fns {} :condition-maps {}}
           [~@edge-defs]))))



(defmacro defnode 
  "
  ⚙️ Define each node behavior get the current state and execut the body on the state
  The node function must always return a map with the values to be used in the future states
  Anything saved in the :completion key will be pushed into history.
  will be traversed to under the :next key
  The state in general is of the form
  {:key1  <output for node behavior>
   :key2 ...
   :history [[:node-name {:bosquet/completion....}]..]
   :trace [[:node1 :node2]...[:node-n :end]]   ;the actual path taken by this agent run
  } 
  Return any keys that you would like to return in the state that you want future nodes to use 
  {:my-key 'some value returned by the llm or the result of a search'
   :completion 'the result of an llm call that will be added to history'
  }
  "
  [name args & body]
  `(defn ~name ~args
     (let [result# (do ~@body)]
       result#)))

(defn run-graph 
  "
  🏃 Graph executor
  run a digraph given 
  - graph-def - a loom digraph, branch-conditions and functions to select branches
  {:graph <loom graph>
   :condition-maps <optional {:source-node {condition destination-node}..}>
   :condition-fns <optional {:source-node (fn[state] return values in condi}tion in the condtions map to branch on)}>
  }
  - a node-map mapping the nodes to behaviors
  - the entry node 
  - recording the history(completions) as it traverses the graph  
  "
  [graph-def node-map entry-node {:keys [trace __pos history] :as input}]

  (loop [state (assoc input :__pos (or entry-node (first (lg/nodes (:graph graph-def))))
                            :history (or history [])
                            :trace (or trace []))]
    (let [pos (:__pos state)]
      (if (= (:__pos state) :end)
        (dissoc state :__pos)
        (let [{:keys [graph condition-fns condition-maps]} graph-def
              node-fn (get node-map pos)
              current-state (node-fn state)
              state (merge state current-state)
              next-node (if (condition-maps pos) 
                        (-> ((condition-fns pos) state)
                            ((condition-maps pos)))
                        (first (lg/successors graph pos)))
              #_#_next-node (if next next (first (lg/successors graph pos)))]
          (recur (-> state
                     (update :history conj [pos (:completion state)])
                     (assoc :__pos next-node)
                     (update :trace conj [pos next-node]))))))))


(defmacro defagent 
  "
  🎯 One-stop macro to define and run an agent
  To run the agent call (<name> graph entry-node nodemap)
  "
  [name graph entry-node node-map]
  `(defn ~name [initial-state#]
     (run-graph ~graph ~node-map ~entry-node initial-state#)))

(comment 
  (require '[bosquet.llm.wkk :as wkk])
  (def llm (g/llm wkk/ollama wkk/model-params {:model "gemma3:12b"} wkk/var-name :answer))
  (defnode categorize 
    [state]
    (let  [completion (g/generate
                                        [[:system "Is the user asking a question (general inquiry) or making a request to produce code? Reply only with: question or code."]
                                         [:user "{{input}}"]
                                         [:assistant llm]] state) 
           what (get-in completion [:bosquet/completions :answer])
           code? (re-find #"(?i)code" what)] 
        {:completion completion
         :code? code?}))

  (defnode codegen 
    [state]
    (let [completion (g/generate
                                       [[:system "Write code for this request"]
                                        [:user "{{input}}"]
                                        [:assistant llm]] state)]
      {:completion completion
       :code (get-in completion [:bosquet/completions :answer])}))

  (defnode qa 
    [state]
    (let [completion (g/generate 
                                       [[:system "Answer concisely"]
                                        [:user "{{input}}"]
                                        [:assistant llm]] state)]
     {:completion completion
      :answer (get-in completion [:bosquet/completions :answer]) }))

  (defnode summarize
    [state]
    (let [conversation (->> state 
                            :history
                            (mapcat #(:bosquet/conversation (second %) ) )
                            vec)
          prompt [[:system "Summarize the following conversation and decisions in one or two sentences"]
                   [:user conversation]
                   [:assistant llm]]
          completion (g/generate prompt {})]
      {:completion completion
       :summary (get-in completion [:bosquet/completions :answer])}))

  (defgraph agent-graph
    [:categorize {true :codegen false :qa} (fn[state] (boolean (:code? state)))]
    [:qa :summarize]
    [:codegen :summarize]
    [:summarize :end])

  (lio/view (:graph agent-graph ))

  (defagent run-agent agent-graph
    :categorize
    {:categorize categorize
     :qa qa
     :codegen codegen
     :summarize summarize})

  (run-agent {:input "is pluto a planet"})
  (run-agent {:input "Write a bash script to count files in a folder."})
 )

     
