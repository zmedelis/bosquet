(ns bosquet.agent.graph
  (:require [loom.attr :as attr]
            [loom.graph :as lg]
            [loom.io :as lio]
            [bosquet.llm.generator :as g]
            [taoensso.timbre :as timbre]))

(defmacro defgraph
 "
 Create a loom digraph that can be run within the agent (edges + optional labels)
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
  Define each node behavior get the current state and execut the body on the state
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
  Graph executor
  run a digraph given 
  - graph-def - a loom digraph, branch-conditions and functions to select branches
  {:graph <loom graph>
   :condition-maps <optional {:source-node {condition destination-node}..}>
   :condition-fns <optional {:source-node (fn[state] return values in condi}tion in the condtions map to branch on)}>
  }
  - a node-map mapping the nodes to behaviors
  - the entry node 
  recording the history(completions) as it traverses the graph  
  If a node returns {:interrupt true}, execution pauses and returns interrupt state
  "
  [graph-def node-map entry-node {:keys [trace __pos history] :as input}]

  (loop [state (assoc input :__pos (or entry-node (first (lg/nodes (:graph graph-def))))
                            :history (or history [])
                            :trace (or trace []))]
    (let [pos (:__pos state)]
      (if (= (:__pos state) :end)
        (-> state (dissoc :__pos) (dissoc :completion))
        (let [{:keys [graph condition-fns condition-maps]} graph-def
              node-fn (get node-map pos)
              _ (or node-fn (timbre/error {:msg "Failed to get next node" :history (:history state)} ))
              current-state (node-fn state)
              state (merge state current-state)]
          (if (:interrupt current-state)
            {:status :interrupted
             :node pos
             :state (-> state (dissoc :interrupt))
             :graph-def graph-def
             :node-map node-map
             :message (:interrupt-message current-state "Human intervention required")}
            (let [next-node (if (condition-maps pos) 
                            (-> ((condition-fns pos) state)
                                ((condition-maps pos)))
                            (first (lg/successors graph pos)))]
              (recur (-> state
                         (update :history conj [pos (:completion state)])
                         (assoc :__pos next-node)
                         (update :trace conj [pos next-node]))))))))))

(defn resume-graph
  "
  Resume graph execution after human intervention
  Takes the interrupted state and updated user input to continue execution
  "
  [interrupted-result updated-state]
  (let [{:keys [graph-def node-map state]} interrupted-result
        {:keys [trace __pos history]} state
        next-node (if (get-in graph-def [:condition-maps __pos])
                    (-> ((get-in graph-def [:condition-fns __pos]) updated-state)
                        ((get-in graph-def [:condition-maps __pos])))
                    (first (lg/successors (:graph graph-def) __pos)))
        merged-state (merge state updated-state)]
    (run-graph graph-def 
               node-map 
               next-node 
               (-> merged-state
                   (update :history conj [__pos (:completion merged-state)])
                   (update :trace conj [__pos next-node])))))


(defmacro defagent 
  "
  One-stop macro to define and run an agent
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

  ;; Example with interrupts for human intervention
  (defnode review-request
    [state]
    (let [completion (g/generate
                       [[:system "Analyze this request and suggest an approach"]
                        [:user "{{input}}"]
                        [:assistant llm]] state)]
      {:completion completion
       :analysis (get-in completion [:bosquet/completions :answer])
       :interrupt true
       :interrupt-message "Please review the analysis and provide feedback before proceeding"}))

  (defnode implement-with-feedback
    [state]
    (let [prompt [[:system "Implement the request considering this analysis and human feedback:
                           Analysis: {{analysis}}
                           Human Feedback: {{human-feedback}}"]
                  [:user "{{input}}"]
                  [:assistant llm]]
          completion (g/generate prompt state)]
      {:completion completion
       :implementation (get-in completion [:bosquet/completions :answer])}))

  (defnode final-review
    [state]
    (let [prompt [[:system "Review the implementation and provide final recommendations"]
                  [:user "Implementation: {{implementation}}"]
                  [:assistant llm]]
          completion (g/generate prompt state)]
      {:completion completion
       :review (get-in completion [:bosquet/completions :answer])
       :interrupt true
       :interrupt-message "Final review complete. Approve or request changes?"}))

  (defgraph interactive-agent-graph
    [:review-request :implement-with-feedback]
    [:implement-with-feedback :final-review]
    [:final-review :end])

  (defagent interactive-agent interactive-agent-graph
    :review-request
    {:review-request review-request
     :implement-with-feedback implement-with-feedback
     :final-review final-review})

  ;; Example usage with interrupts:
  ;; Start the agent
  (def result1 (interactive-agent {:input "Create a user authentication system"}))
  
  ;; If interrupted, result1 will be:
  ;; {:status :interrupted
  ;;  :node :review-request  
  ;;  :state {...}
  ;;  :message "Please review the analysis and provide feedback before proceeding"}

  ;; Human provides feedback and resumes
  (def result2 (resume-graph result1 {:human-feedback "Focus on security best practices and use JWT tokens"}))
  
  ;; If interrupted again at final-review, provide approval
  (def final-result (resume-graph result2 {:approval "Approved - looks good to implement"}))

  ;; Example of checking for interrupts:
  (defn run-with-interrupts 
    [agent initial-state]
    (loop [result (agent initial-state)]
      (if (= (:status result) :interrupted)
        (do 
          (println (str "Interrupted at node: " (:node result)))
          (println (str "Message: " (:message result)))
          (println "Waiting for human input...")
          ;; In a real application, you'd collect user input here
          ;; For demo, we'll simulate continuing
          (let [user-input (case (:node result)
                            :review-request {:human-feedback "Use secure authentication"}
                            :final-review {:approval "Approved"}
                            {})]
            (recur (resume-graph result user-input))))
        result)))

  (run-with-interrupts interactive-agent {:input "Build a login system"}))

     
