(ns bosquet.agent.react
  (:import
    [bosquet.agent.wikipedia Wikipedia2])
  (:require
    [bosquet.agent.agent :as a]
    [bosquet.agent.agent-mind-reader :as mind-reader]
    [bosquet.generator :as generator]
    [bosquet.template.read :as template]
    [clojure.string :as string]
    [taoensso.timbre :as timbre]
    [taoensso.timbre.appenders.core :as appenders]))

(timbre/merge-config!
  {:appenders {:println {:enabled? false}
               :spit    (appenders/spit-appender {:fname "bosquet.log"})}})

(def prompt-palette (template/load-palettes "resources/prompt-palette/agent"))

(defn generate-thoughts
  "Generate ReAct thoughts.
  `ctx` has needed data points
  `prompt-palette` has all the prompts needed for ReAct
  `prompt-key` is the key to the prompt to be used to start the generation."
  [ctx prompt-palette prompt-key]
  (let [x (generator/complete prompt-palette ctx [prompt-key
                                                  :thoughts])
        gen-output (get x :thoughts)
        full-output (get x prompt-key)
        prompt (string/replace full-output gen-output #_(re-pattern (format "(?s)%s^" gen-output)) "")
        ]
    (timbre/debugf "\n\n***** Prompt:\n%s\n" prompt)
    (timbre/debugf "\n\n***** Generated part:\n%s\n" gen-output)
    ;; :resoning-trace will contain only the thoughts from before,
    ;; most recent observation goes into :thoughts
    {:reasoning-trace prompt
     :thoughts        gen-output}))

(defn focus-on-observation
  "Get the sentence a the position `lookup-index` from the observation."
  [{:keys [lookup-db lookup-index]}]
  (get lookup-db lookup-index))

(defn solve-task
  "Solve a task using [ReAct](https://react-lm.github.io)

  :react/task contains a question or a claim to be solved"
  [agent {:keys [task] :as initial-ctx}]
  (a/print-thought "I need to figure out the following question" task)
  (let [{thoughts        :thoughts
         reasoning-trace :reasoning-trace}
        (generate-thoughts
          (assoc initial-ctx :reasoning-trace "")
          prompt-palette :react/step-0)]
    (loop [step            1
           ctx             initial-ctx
           thoughts        thoughts
           reasoning-trace reasoning-trace]
      (let [{:keys [action thought parameters] :as action-ctx}
            (mind-reader/find-action step thoughts)
            ctx                 (merge ctx action-ctx {:step step})
            _                   (a/print-indexed-step "Thought" thought step)
            _                   (a/print-action action parameters step)
            observation         (condp = action
                                  :search {:lookup-db    (mind-reader/lookup-index (a/search agent ctx)
                                                           parameters)
                                           :lookup-index 0}
                                  :lookup (a/lookup agent ctx)
                                  :finish (a/finish agent ctx))
            current-observation (focus-on-observation observation)]
        (a/print-indexed-step "Observation" current-observation step)
        (if (= step 2)
          nil
          (let [
                {thoughts        :thoughts
                 reasoning-trace :reasoning-trace}
                (generate-thoughts
                  (assoc ctx
                    :step step
                    :rasoning-trace
                    (str reasoning-trace "\n" thought)
                    :observation current-observation)
                  prompt-palette
                  :react/step-n)]
            (recur (inc step) ctx thoughts reasoning-trace)))))))

(comment

  (def question "Author David Chanoff has collaborated with a U.S. Navy admiral who served as the ambassador to the United Kingdom under which President?")
  (def w (a/Wikipedia2.))
  (solve-task w {:task question})

  (generate-thoughts
    {:reasoning-trace "Full history." :observation "Observation 10" :step 10}
    prompt-palette :react/step-n)

  (with-redefs [generator/complete (fn [_ctx _prompts prompt-keys]
                                     {:thoughts "I am thinking..."
                                      (first prompt-keys)
                                      "You are ReAct! I am thinking...\nAnd then again thinking"})]
    (generate-thoughts nil nil :test)))
