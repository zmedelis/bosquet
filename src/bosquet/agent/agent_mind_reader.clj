(ns bosquet.agent.agent-mind-reader
  (:require
   [clojure.string :as string]))

(defn find-first-action
  "Read agents thoughts and actions. Return the first action found."
  [agent-mind]
  (let [[_ action parameter] (re-find #".*?Action 1:(.*?)\[(.*?)\]" agent-mind)]
    {:action    (string/trim action)
     :parameter (string/trim parameter)}))
