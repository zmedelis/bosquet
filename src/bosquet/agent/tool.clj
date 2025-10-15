(ns bosquet.agent.tool
  (:require [taoensso.timbre :as timbre]))

(defprotocol Tool
  (my-name [this])
  (search [this ctx])
  (lookup [this ctx])
  (finish [this ctx]))

(defn call-tool [agent action ctx]
  (condp = action
    :search (let [result (search agent ctx)]
              {:lookup-db    [[0 true result]]
               :lookup-index 0})
    :lookup (lookup agent ctx)
    :finish (finish agent ctx)))

;;
;; Logging tool/agent thinking/acting
;;

(defn print-indexed-step [action plan step]
  (timbre/info (format "%s: %s" (name action) step))
  (timbre/info plan))

(defn print-action [action parameters step]
  (timbre/info "\nAct: " step)
  (timbre/info "- Action: " (name action))
  (timbre/info "- Parameters: " parameters))

(defn print-thought [plan content]
  (timbre/info (str "\n" plan ":"))
  (timbre/info content))

(defn print-result [result]
  (timbre/info "Agent found the solution: " result))

(defn print-too-much-thinking-error [steps]
  (timbre/info
   (format "\nAgent was thinking for %s steps and failed to find a solution" steps)))
