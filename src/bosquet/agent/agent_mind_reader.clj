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
    ;; 'Observation' at the end is optional because there will be none when 'Action=Finish'
    (format "(?s).*?(Thought %s:.*?)(Action %s:(.*?)\\[(.*?)\\])(\\nObservation %s:)?"
      cycle cycle cycle)))

(defn find-action
  "Read agent's thoughts and actions. Find the action in its `cycle` of thinking."
  [step agent-mind]
  (let [[_ thought action action-verb action-param] (re-find (action-re step) agent-mind)]
    {:thought    (string/trim (str thought action))
     :action     (normalize-action action-verb)
     :parameters (string/trim action-param)}))

(defn split-sentences
  "Split `text` into sentences."
  [text]
  ;; Naive regex based implementation
  (string/split text #"(?s)(?<=[^A-Z].[.?])\s+(?=[A-Z])"))

(defn lookup-index
  "Construct a `query` lookup index for the `content`.
  It will return a seqence of triplets.
  ```
  [sentence-index has-query sentence]
  ```
  where `sentence-index` is the index of the sentence in the `content`,
  `has-query` is a boolean indicating if the sentence contains the `query`,
  `sentence` is the sentence itself."
  [query content]
  (vec
    (map-indexed
      (fn [idx sentence]
        [idx
         (string/includes? (string/lower-case sentence) (string/lower-case query))
         sentence])
      (split-sentences content))))
