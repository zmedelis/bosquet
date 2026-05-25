(ns bosquet.agent.react-agent
  (:require
   [bosquet.agent.graph :refer [defgraph defnode defagent]]
   [bosquet.llm.generator :as g]
   [bosquet.llm.tools :as tools]
   [bosquet.llm.wkk :as wkk]
   [cheshire.core]
   [clojure.string :as str]
   [taoensso.timbre :as timbre]))


(defn create-react-prompt
  "Create ReAct prompt with examples and current state.
   Rules:
   - Generate a step-by-step plan (Thought) before each action.
   - Generate only one Action per response.
   - Each Thought/Action must reference only the Task or previous Observations.
   - LLM may choose Finish[result] when the task is complete.
   - Step numbers increment continuously."
  [{:keys [examples task plan step reasoning-trace observation tool-descriptions max-steps]}]
  (let [base-prompt
        (str
          (when examples
            (str "EXAMPLES:\n" examples "\n\nCURRENT TASK:\n"))
          "Task: " task "\n\n"
          (when plan (str "Plan:\n" plan "\n\n"))
          "You have access to the following tools:\n" tool-descriptions "\n\n"
          "Use actual tool names with quoted strings: read-file[\"src/core.clj\"] or bash[\"ls -la\"]\n\n"
          "CRITICAL:\n"
          "1. Before taking any action, write a Thought that reasons about the current state and what to do next.\n"
          "2. Generate ONLY one Thought and one Action per response. DO NOT generate Observations.\n"
          "3. Each Thought/Action must be grounded in the Task or previous Observations — do not assume state you have not observed.\n"
          "4. Do not invent file contents, command outputs, or values not present in the Task or Observations.\n"
          "5. If an Observation contains an error, reason about the cause and try an alternative approach.\n"
          "6. When the task is complete, emit Finish[answer] where answer is ONLY the direct answer to the Task — no working, restatement, or explanation.\n"
          (when max-steps
            (str "7. You have at most " (- max-steps (dec step)) " steps remaining. Budget your actions accordingly.\n"))
          "\nContinue step numbering from current step = " step ".\n"
          "Thought numbers must match the step number.\n"
          "Action numbers must match the step number.\n\n"
          "Format your response EXACTLY as:\n"
          "Thought " step ": [your reasoning about current state and next step]\n"
          "Action " step ": tool-name[parameters] OR Finish[result]\n"
          "STOP HERE. Wait for system response.\n")]
    (if reasoning-trace
      (str base-prompt "\n" reasoning-trace
           (when observation (str "Observation " step ": " observation "\n")))
      base-prompt)))


(defn create-plan-prompt
  [{:keys [task tool-descriptions]}]
  (str
    "Task: " task "\n\n"
    "You have access to the following tools:\n" tool-descriptions "\n\n"
    "Before taking any action, produce a concise numbered plan of the steps needed to complete this task.\n"
    "- Be specific about which tools you will use and in what order.\n"
    "- If the task is ambiguous, note what you are assuming.\n"
    "- Keep it short — this is a plan, not an execution.\n\n"
    "Respond ONLY with:\n"
    "Plan:\n"
    "1. [step]\n"
    "2. [step]\n"
    "..."))

(defn create-verify-prompt
  [{:keys [task plan step reasoning-trace verification-target]}]
  (str
    "Task: " task "\n\n"
    (when plan (str "Plan:\n" plan "\n\n"))
    "Trace so far:\n" reasoning-trace "\n\n"
    "You just completed an action. Verify: " verification-target "\n\n"
    "CRITICAL:\n"
    "1. If you can confirm success from the last Observation alone, reason about it directly.\n"
    "2. If confirmation requires checking current state, use a tool.\n"
    "3. If verification passes, reason about the next step in the plan.\n"
    "4. If verification fails, reason about what went wrong.\n\n"
    "Format your response EXACTLY as:\n"
    "Thought " step ": [your verification reasoning]\n"
    "Action " step ": tool-name[parameters] OR Finish[result]\n"
    "STOP HERE. Wait for system response.\n"))


(defn create-clarify-prompt
  [{:keys [task plan reasoning-trace tool-descriptions]}]
  (str
    "Task: " task "\n\n"
    (when plan (str "Plan:\n" plan "\n\n"))
    (when reasoning-trace (str "Trace so far:\n" reasoning-trace "\n\n"))
    (when tool-descriptions
      (str "Available tools:\n" tool-descriptions "\n\n"))
    "Decide whether you can start this task right now.\n\n"
    "You are a capable, decisive agent. In almost all cases you CAN proceed using common\n"
    "sense, reasonable default assumptions, and the tools above. Vague or informal wording\n"
    "is NOT a reason to ask — interpret it the way a competent person obviously would. Never\n"
    "ask about anything a tool can fetch (weather, time, prices, lookups). When in doubt, PROCEED.\n\n"
    "Only ask the user if the task is genuinely impossible to even attempt — i.e. a required\n"
    "input is missing AND cannot be inferred or fetched. In that rare case respond with exactly:\n"
    "Clarify: [one specific question]\n\n"
    "Otherwise (the normal case) respond with exactly:\n"
    "Proceed"))


(defn prepare-tools
  "Create a comprehensive tool map with symbols, descriptions, and metadata"
  [tool-vars]
  (into {} 
    (map (fn [tool-var]
           (let [tool-name (keyword (str/lower-case (name (:name (meta tool-var)))))
                 tool-fn (tools/tool->function tool-var)
                 func-info (get tool-fn :function)]
             [tool-name {:symbol tool-var
                         :name (:name func-info)
                         :description (:description func-info)
                         :metadata func-info}]))
         tool-vars)))

(defn get-tool-descriptions
  "Get tool descriptions from comprehensive tool map"
  [tool-map]
  (str/join ", " 
    (map (fn [[_ tool-info]]
           (str (:name tool-info) " - " (:description tool-info)))
         tool-map)))

(defn ->llm
  [llm-spec var-name & [tool-array]]
  (let [model-params (if tool-array 
                       (merge (wkk/model-params llm-spec) {wkk/tools tool-array})
                       (wkk/model-params llm-spec))]
    (g/llm (:llm llm-spec) wkk/model-params model-params wkk/var-name var-name wkk/cache false)))

(defn parse-action-response
  "Parse LLM response to extract action and parameters"
  [response]
  (when (and response (string? response))
    (cond 
     ;; Check for Final Answer first
     (re-find #"Finish" response)
     (let [raw    (some-> (re-find #"Finish\s*(.+)" response) second str/trim
                          (str/replace #"^:\s*" ""))
           answer (when raw
                    (if-let [m (re-find #"\[(.*)\]" raw)]
                      (str/trim (second m))   ;; unwrap Finish[...] -> ...
                      raw))]
       {:action :final-answer
        :full-response response
        :final-answer answer})
     
     ;; Try Action N: format with parameters in brackets
     (re-find #"Action\s+(?:\d+|N):\s*(\S+)" response)
     (let [action-match (re-find #"Action\s+(?:\d+|N):\s*(.+)" response)
           [_ action-text] action-match
           ;; Parse tool-name[params] format  
           [tool-name params] (if-let [bracket-match (re-find #"^([^\[\s]+)\[([^\]]+)\]" action-text)]
                               (let [tool-name (second bracket-match)
                                     params-str (str "[" (nth bracket-match 2) "]")]
                                 [tool-name 
                                  (try
                                    (clojure.edn/read-string params-str)
                                    (catch Exception _ nil))])
                               ;; No brackets - tool name only
                               [action-text nil])]
       {:action (keyword (str/lower-case tool-name))
        :parameters params
        :full-response response})
     
     :else nil)))

(defn execute-tool
  "Execute a tool with parameters and return the result"
  [tool-fn tool-name parameters]
  (try
    (let [result (apply tool-fn parameters)
          clean-result (str result)]
      (timbre/debugf "REACT-ACT - Direct tool call: %s with params: %s -> %s" tool-name parameters result)
      {:success true :result clean-result})
    (catch Exception e
      (let [error-msg (str "Error calling " tool-name ": " (.getMessage e))]
        (timbre/errorf "Tool execution error: %s" error-msg)
        {:success false :result error-msg}))))


(defn handle-tool-execution
  "Handle tool execution with parameters"
  [tool-info tool-name action-data llm-spec state]
  (let [tool-fn (:symbol tool-info)
        parameters (:parameters action-data)]
    (timbre/debugf "HANDLE-TOOL-EXECUTION - tool: %s, parameters: %s, action-data: %s" tool-name parameters action-data)
    (if parameters
      ;; Direct tool invocation with extracted parameters
      (let [exec-result (execute-tool tool-fn tool-name parameters)]
        (timbre/debugf "HANDLE-TOOL-EXECUTION - Using direct execution")
        {:observation (:result exec-result)
         :finished? false})
      ;; No parameters extracted - treat as error
      (do
        (timbre/debugf "HANDLE-TOOL-EXECUTION - No parameters found, treating as error")
        {:observation (str "No parameters provided for tool '" tool-name "'")
         :finished? false}))))

(defnode react-think
  [{:keys [llm-spec examples tool-map task step reasoning-trace observation max-iterations] :as state}]
  (if (>= step max-iterations)
    {:completion "Maximum iterations reached"
     :finished? true
     :result "Failed to solve within maximum iterations"}
    (let [tool-descriptions (get-tool-descriptions tool-map)
          prompt (create-react-prompt (assoc state :tool-descriptions tool-descriptions))
          system-msg (get state :system-prompt "You are a helpful assistant that can use tools.")
          completion (g/generate 
                      [[:system system-msg]
                       [:user prompt]
                       [:assistant (->llm llm-spec :react-response)]] 
                      {})
          response (get-in completion [:bosquet/completions :react-response])
          action-data (parse-action-response response)]
      {:completion completion
       :response response
       :action-data action-data})))

(defnode react-act
  [{:keys [action-data tool-map step max-iterations llm-spec] :as state}]
  (cond
    (>= step max-iterations)
    {:observation "Maximum iterations reached"
     :finished? true
     :result "Failed to solve within maximum iterations"}

    (not action-data)
    {:observation "Failed to parse action from response"
     :finished? false}

    (= (:action action-data) :final-answer)
    (let [final-answer (:final-answer action-data)
          final-trace (str (or (:reasoning-trace state) "")
                           (:response state) "\n")]
      {:observation "Task completed"
       :finished? true
       :result (or final-answer "Task completed successfully")
       :reasoning-trace final-trace
       :step (inc step)})

    :else
    (let [tool-name (:action action-data)
          tool-info (get tool-map tool-name)]
      (if tool-info
        (handle-tool-execution tool-info tool-name action-data llm-spec state)
        {:observation (str "Tool '" tool-name "' not found")
         :finished? false}))))

(defnode react-update-trace
  [{:keys [response observation step reasoning-trace] :as state}]
  (let [new-trace (str (or reasoning-trace "")
                      response "\n"
                      "Observation " step ": " observation "\n")]
    {:reasoning-trace new-trace
     :step (inc step)}))

;; Entry node for the planning agent.
;; - If `:clarify?` is falsey (auto mode) this node is a no-op and the agent
;;   proceeds straight to planning, letting the plan make assumptions.
;; - If `:clarify?` is truthy, ask the LLM (via create-clarify-prompt) whether a
;;   clarification is required. If the LLM can resolve the ambiguity itself it
;;   continues; otherwise it emits `Clarify: <question>` and we call the
;;   human-in-the-loop `:input-fn` with that question. The answer is folded back
;;   into `:task` so plan/think can use it.
;; - If clarification is required but cannot be obtained (no `:input-fn`, it
;;   throws, or returns blank) we set `:clarify-failed?` and terminate the graph.
(defnode clarify
  [{:keys [llm-spec tool-map task clarify? input-fn] :as state}]
  (if-not clarify?
    {:completion nil} ;; auto mode: skip clarification, plan will assume
    (let [tool-descriptions (get-tool-descriptions tool-map)
          prompt (create-clarify-prompt (assoc state :tool-descriptions tool-descriptions))
          system-msg (get state :system-prompt "You are a decisive agent that proceeds on its own whenever reasonably possible and only asks the user when truly blocked.")
          completion (g/generate
                       [[:system system-msg]
                        [:user prompt]
                        [:assistant (->llm llm-spec :clarify-response)]]
                       {})
          response (get-in completion [:bosquet/completions :clarify-response])
          clarify-match (re-find #"(?i)Clarify:\s*(.+)" response)]
      (if-not clarify-match
        ;; LLM resolved the ambiguity on its own -> continue to plan
        {:completion completion
         :clarification :resolved}
        (let [question (str/trim (second clarify-match))]
          (if-not (fn? input-fn)
            {:completion completion
             :clarify-failed? true
             :finished? true
             :result (str "Clarification required but no :input-fn was provided. Question: " question)}
            (let [answer (try (input-fn question) (catch Exception e (timbre/error e "clarify input-fn failed") nil))]
              (if (str/blank? (str answer))
                {:completion completion
                 :clarify-failed? true
                 :finished? true
                 :result (str "Clarification failed; no answer provided for: " question)}
                {:completion completion
                 :clarification answer
                 :task (str task "\n\nClarification — " question "\nAnswer: " answer)}))))))))

;; Produce a concise numbered plan for the task (create-plan-prompt) and stash it
;; under `:plan` so the think/verify steps can reason against it.
(defnode plan
  [{:keys [llm-spec tool-map] :as state}]
  (let [tool-descriptions (get-tool-descriptions tool-map)
        prompt (create-plan-prompt (assoc state :tool-descriptions tool-descriptions))
        system-msg (get state :system-prompt "You are a planning assistant.")
        completion (g/generate
                     [[:system system-msg]
                      [:user prompt]
                      [:assistant (->llm llm-spec :plan-response)]]
                     {})]
    {:completion completion
     :plan (get-in completion [:bosquet/completions :plan-response])}))

;; Runs after EVERY act (react-act always routes here). It both records the trace
;; and is the single node that decides whether the run is really done:
;; - act proposed a final answer (Finish): when `:verify?` is on, ask the LLM to
;;   confirm it — confirmed -> end, rejected -> back to think to try again; when
;;   `:verify?` is off the answer is trusted -> end.
;; - act ran a tool: record the observation, then (when `:verify?` is on) verify
;;   it — Finish -> end, a tool action -> run it inline, otherwise -> think.
;; - act hard-stopped (max iterations) -> end.
;; It clears `:observation` (now folded into the trace) and increments `:step`.
(defnode verify
  [{:keys [llm-spec tool-map action-data response observation step reasoning-trace
           verify? max-iterations result finished?] :as state}]
  (let [proposed-finish? (= (:action action-data) :final-answer)
        ;; For a tool step the think response + observation are not in the trace
        ;; yet, so add them. For a proposed finish react-act already appended the
        ;; response, so don't duplicate it.
        trace+ (if proposed-finish?
                 (or reasoning-trace "")
                 (str (or reasoning-trace "")
                      response "\n"
                      "Observation " step ": " observation "\n"))]
    (cond
      (and finished? (not proposed-finish?))
      {:finished? true :result result :observation nil}

      (not verify?)
      (if proposed-finish?
        {:finished? true :result result :observation nil}
        {:finished? false :reasoning-trace trace+ :step (inc step) :observation nil})

      (>= step max-iterations)
      {:finished? true
       :reasoning-trace trace+
       :observation nil
       :result (if proposed-finish? result "Failed to complete within maximum iterations")}

      :else
      (let [vstate (assoc state
                          :reasoning-trace trace+
                          :verification-target
                          (or (:verification-target state)
                              (if proposed-finish?
                                (str "that the proposed final answer is correct: " result)
                                "that the last action achieved its intended effect")))
            prompt (create-verify-prompt vstate)
            system-msg (get state :system-prompt "You are a careful agent that verifies its own work.")
            completion (g/generate
                         [[:system system-msg]
                          [:user prompt]
                          [:assistant (->llm llm-spec :verify-response)]]
                         {})
            vresponse (get-in completion [:bosquet/completions :verify-response])
            vaction (parse-action-response vresponse)]
        (cond
          (= (:action vaction) :final-answer)
          {:completion completion
           :reasoning-trace (str trace+ vresponse "\n")
           :observation nil
           :step (inc step)
           :finished? true
           :result (or (:final-answer vaction) result "Task verified complete")}

          (and vaction (get tool-map (:action vaction)))
          (let [tool-name (:action vaction)
                tool-info (get tool-map tool-name)
                exec (handle-tool-execution tool-info tool-name vaction llm-spec state)]
            {:completion completion
             :reasoning-trace (str trace+ vresponse "\n"
                                   "Observation " step ": " (:observation exec) "\n")
             :observation nil
             :step (inc step)
             :finished? false})

          ;;rejected
          :else
          {:completion completion
           :reasoning-trace (str trace+ vresponse "\n")
           :observation nil
           :step (inc step)
           :finished? false})))))

(defgraph react-graph
  [:react-think :react-act]
  [:react-act {:continue :react-update-trace :finish :end} 
   (fn [state] (if (:finished? state) :finish :continue))]
  [:react-update-trace :react-think])

(defagent ReAct-agent react-graph
  :react-think
  {:react-think react-think
   :react-act react-act
   :react-update-trace react-update-trace})

(defn create-ReAct-agent
  "Create a ReAct agent with graph-based step control.
  
  Args:
  - tools: Vector of tool function vars with metadata
  - prompt: String prompt or map with :examples key for few-shot examples
  - max-iterations: Maximum number of reasoning steps (default: 15)  
  - llm: LLM specification for bosquet.llm.generator
  
  Returns a function that executes the ReAct reasoning loop."
  [tools prompt max-iterations llm-spec]
  (let [max-steps (or max-iterations 15)
        system-prompt (cond
                        (string? prompt) prompt
                        (map? prompt) (:system prompt)
                        :else "You are a helpful reasoning agent.")
        examples (cond
                   (map? prompt) (:examples prompt)
                   (sequential? prompt) prompt
                   :else nil)]
    (fn [initial-state]
      (let [tool-map (prepare-tools tools)
            state (merge initial-state
                        {:tool-map tool-map
                         :examples examples
                         :system-prompt system-prompt
                         :max-iterations max-steps
                         :llm-spec llm-spec
                         :step 1
                         :reasoning-trace ""})]
        (ReAct-agent state)))))

;; Planning agent
(defgraph plan-react-graph
  [:clarify {:terminate :end :continue :plan}
   (fn [state] (if (:clarify-failed? state) :terminate :continue))]
  [:plan :react-think]
  [:react-think :react-act]
  [:react-act :verify]
  [:verify {:continue :react-think :finish :end}
   (fn [state] (if (:finished? state) :finish :continue))])

(defagent planning-react-agent plan-react-graph
  :clarify
  {:clarify clarify
   :plan plan
   :react-think react-think
   :react-act react-act
   :verify verify})

(defn create-planning-agent
  "Create a planning ReAct agent
    [clarify?] -> [plan] -> [think] -> [act] -> [verify] -> [think] -> ...
                              ^                    |
                              └──── reject ────────┘
  Args (first four mirror create-ReAct-agent):
  - tools          : vector of tool function vars with metadata
  - prompt         : string system prompt or map with :system / :examples
  - max-iterations : max reasoning steps (default 15)
  - llm-spec       : LLM specification for bosquet.llm.generator
  - opts (map, optional):
      :clarify?            run the clarify step (default false = auto mode)
      :verify?             run the verify step after each act (default true)
      :input-fn            (fn [question] -> answer) human-in-the-loop used by
                           clarify. Missing/blank/throwing answer terminates the graph.
      :verification-target what verify should confirm about each action
      :clarification-question seed concern for the clarify step

  Any of :clarify? :verify? :input-fn :task etc. can also be supplied per-call in
  the initial-state map passed to the returned function."
  [tools prompt max-iterations llm-spec
   & [{:keys [clarify? verify? input-fn verification-target clarification-question]
       :or {clarify? false verify? true}}]]
  (let [max-steps (or max-iterations 15)
        system-prompt (cond
                        (string? prompt) prompt
                        (map? prompt) (:system prompt)
                        :else "You are a helpful reasoning agent.")
        examples (cond
                   (map? prompt) (:examples prompt)
                   (sequential? prompt) prompt
                   :else nil)]
    (fn [initial-state]
      (let [tool-map (prepare-tools tools)
            state (merge
                    {:clarify? clarify?
                     :verify? verify?
                     :input-fn input-fn
                     :verification-target verification-target
                     :clarification-question clarification-question}
                    initial-state
                    {:tool-map tool-map
                     :examples examples
                     :system-prompt system-prompt
                     :max-iterations max-steps
                     :llm-spec llm-spec
                     :step 1
                     :reasoning-trace ""})]
        (planning-react-agent state)))))

(comment
  ;; Example usage:
  ;; The tool fns live in their own namespaces; load them so the #'var-quotes resolve:
  (require 'bosquet.tool.math 'bosquet.tool.weather)


  (defn ^{:desc "Search for information"} search-info  
    [^{:type "string" :desc "Search query"} query]
    (str "Search results for: " query " - This is a mock search result."))
  
  ;; Create LLM spec
  (def llm-spec {:llm wkk/ollama
                 wkk/model-params {:model "qwen3.6" :temperature 0}})

  (->llm llm-spec :test-response)  
  ;; Create ReAct agent
  (def my-agent 
    (create-ReAct-agent 
      [#'bosquet.tool.math/add #'bosquet.tool.weather/get-current-weather #'search-info]
      {:system "You are a calculator. Give clean, concise answers" }
      10
      llm-spec))
 
  ;; Run the agent  
  (my-agent {:task "What is 123 + 456?"})

  (my-agent {:task "Add 1000 to the current weather in san francisco."})

  (require '[bosquet.template.read :as template])
  (require '[bosquet.agent.tool :refer [search]]) 
  (require '[bosquet.agent.wikipedia :as w]) 

  (defn wiki-tool
     ^{:desc "Lookup or search wikipedia for information"}
      [^{:type "string" :desc "Search query"} query]
    (search (w/->Wikipedia) {:parameters query}))
  
  (def wiki-agent 
    (create-ReAct-agent 
      [#'wiki-tool]
        {:system "You are a able to find results in wikipedia and find answers. Give clean, concise answers"
         :examples (-> (template/load-prompt-palette-edn (clojure.java.io/file "resources/prompt-palette/agent/react.edn")) :react/examples)}
      10
      llm-spec))
  (def question
    "Author David Chanoff has collaborated with a U.S. Navy admiral who served as the ambassador to the United Kingdom under which President?")
  (wiki-agent {:task question})
  (wiki-agent {:task "What city hosted the most recent super bowl and what was the GDP for that state"})

  (g/generate
    [[:system "You are a calculator so only provide the number as the answer without any explanations\nUse any tools to gather information or calculate the answer"]
     [:user "What is the result of adding 1000 to the current weather in san francisco?"]
     [:assistant (->llm llm-spec :tool-response [#'bosquet.tool.weather/get-current-weather #'bosquet.tool.math/add])]])

  ;; Planning agent

  ;; Auto mode (no clarification, verify on):
  (def planner
    (create-planning-agent
      [#'bosquet.tool.math/add #'bosquet.tool.weather/get-current-weather #'wiki-tool]
      {:system "You are a careful agent. Plan, act, and verify your work."}
      20
      llm-spec))
  (planner {:task "What city hosted the most recent super bowl and what was the GDP for that state"})

  ;; Human-in-the-loop clarification via an input function:
  (def clarifying-planner
    (create-planning-agent
      [#'bosquet.tool.math/add #'bosquet.tool.weather/get-current-weather]
      {:system "You are a careful agent. Plan, act, and verify your work."}
      10
      llm-spec
      {:clarify? true
       ;; In a REPL/CLI this reads the human's answer; return blank/nil to abort.
       :input-fn (fn [question]
                   (println "CLARIFY:" question)
                   (read-line))
       :verification-target "that the arithmetic result is correct"}))
  (clarifying-planner {:task "Add some number to the weather."})

  ;; Per-call override (turn clarification off for one run):
  (clarifying-planner {:task "Add 5 to the weather in Paris" :clarify? true})
  )
