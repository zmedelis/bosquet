(ns bosquet.agent.agent-mind-reader
  (:require
    [clojure.string :as string]))

(defn- normalize-action
  "Normalize `action` name to be used as a key to indicate what kind
  of action is requested."
  [action]
  (-> action string/lower-case string/trim keyword))

(defn- action-re
  "Regex to find the action in the agent's mind when it is in a `cycle`"
  [cycle]
  (re-pattern
    (format "(?s).*?Thought %s:(.*?)(Action %s:(.*?)\\[(.*?)\\])\\nObservation %s:"
      cycle cycle cycle)))

(defn find-action
  "Read agent's thoughts and actions. Find the action in its `cycle` of thinking."
  [cycle agent-mind]
  (let [[_ thought _ action param] (re-find (action-re cycle) agent-mind)]
    {:thought    (string/trim thought)
     :action     (normalize-action action)
     :parameters (string/trim param)}))
