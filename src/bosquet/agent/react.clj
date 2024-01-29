(ns bosquet.agent.react
  (:require
   [bosquet.agent.agent-mind-reader :as mind-reader]
   [bosquet.agent.tool :as t]
   [bosquet.llm :as llm]
   [bosquet.llm.generator :as gen]
   [bosquet.template.read :as template]
   [clojure.string :as string]))

#_(timbre/merge-config!
   {:appenders {:println {:enabled? false}
                :spit    (appenders/spit-appender {:fname "bosquet.log"})}})

#_(defn generate-thoughts
    "Generate ReAct thoughts.
  `ctx` has needed data points
  `prompt-palette` has all the prompts needed for ReAct
  `prompt-key` is the key to the prompt to be used to start the generation."
    [{:bosquet/keys [task-prompts services]} ctx prompt-key]
    (let [{{:react/keys [memory thought action]}
           gen/completions} (gen/generate services task-prompts ctx)]

      (tap> {:reasoning-trace trace
             :thoughts        thoughts
             :all x})

    ;; :resoning-trace will contain only the thoughts from before,
    ;; most recent observation goes into :thoughts
      {:reasoning-trace trace
       :thoughts        thoughts}))

(defn focus-on-observation
  "Get the sentence a the position `lookup-index` from the observation."
  [{:keys [lookup-db lookup-index]}]
  ;; last is the position of the sentence in the tuple
  (last (get lookup-db lookup-index)))

(defn solve-task
  "Solve a task using [ReAct](https://react-lm.github.io)

  First `agent` parameter specifies which tool will be used to solve the task.
  Second context parameter gives initialization data to start working
  - `task` is a quesiton ar task formulation for the agent
  - `max-steps` specifies how many thinking steps agent is allowed to do
  it either reaches that number of steps or 'Finish' action, and then terminates."
  [{:bosquet/keys [max-steps tools services task-prompts]
    :or           {max-steps 5}
    :as           cfg}
   task]
  (let [tool              (first tools)
        initial-ctx       {:react/task task
                           :react/step 1}
        _                 (t/print-thought
                           (format "'%s' tool has the following task" (t/my-name tool)) task)
        {{:react/keys [memory thought action] :as x}
         gen/completions} (gen/generate services task-prompts initial-ctx)]
    (tap> x)
    #_(loop [step            1
             ctx             initial-ctx
             thoughts        thoughts
             reasoning-trace reasoning-trace]
        (let [{:keys [action thought parameters] :as action-ctx}
              (mind-reader/find-action step thoughts)
              ctx         (merge ctx action-ctx {:step step})
              _           (t/print-indexed-step "Thought" thought step)
              _           (t/print-action action parameters step)
              observation (t/call-tool tool action ctx)]
          (cond
            ;; Tool failed to find a solution in max steps allocated
            (= step max-steps)
            (do
              (t/print-too-much-thinking-error step)
              nil)

            ;; Tool got to the solution. Print and return it
            (= :finish action)
            (do
              (t/print-result observation)
              observation)

            ;; Continue thinking
            :else
            (let [current-observation (focus-on-observation observation)
                  _                   (t/print-indexed-step "Observation" current-observation step)
                  {:keys [thoughts reasoning-trace]}
                  (generate-thoughts
                   cfg
                   {:react/step            (inc step)
                    :react/reasoning-trace (str reasoning-trace thought)
                    :react/observation     current-observation}
                   :react/step-n)]
              (recur (inc step) ctx thoughts reasoning-trace)))))))

(comment
  (import '[bosquet.agent.wikipedia Wikipedia])
  (def prompt-palette (template/load-palettes "resources/prompt-palette/agent"))
  (def question
    "Author David Chanoff has collaborated with a U.S. Navy admiral who served as the ambassador to the United Kingdom under which President?")

  (solve-task
   {:bosquet/services     llm/default-services
    :bosquet/tools        [(Wikipedia.)]
    :bosquet/task-prompts prompt-palette
    :bosquet/max-steps    5}
   question)
  #__)
