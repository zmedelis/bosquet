(ns bosquet.agent.agent-mind-reader
  (:require
   [clojure.string :as string]))

(defn normalize-action
  "Normalize `action` name to be used as a key to indicate what kind
  of action is requested."
  [action]
  (-> action string/lower-case string/trim keyword))

(defn find-first-action
  "Read agents thoughts and actions. Return the first action found."
  [agent-mind]
  (let [[_ action parameter] (re-find #".*?Action 1:(.*?)\[(.*?)\]" agent-mind)]
    {:action    (normalize-action action)
     :parameter (string/trim parameter)}))
