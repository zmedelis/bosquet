(ns bosquet.agent.tool
  (:require
    [taoensso.timbre :as timbre]
    [io.aviso.ansi :as ansi]
    [taoensso.timbre.appenders.core :as appenders]))

(timbre/merge-config!
  {:appenders {:println {:enabled? false}
               :spit    (appenders/spit-appender {:fname "bosquet.log"})}})

(defprotocol Tool
  (my-name [this])
  (search [this ctx])
  (lookup [this ctx])
  (finish [this ctx]))

(defn call-tool [agent action ctx]
  (condp = action
    :search (let [result (search agent ctx)]
              {:lookup-db    [[0 true result]] #_(mind-reader/lookup-index parameters result)
               :lookup-index 0})
    :lookup (lookup agent ctx)
    :finish (finish agent ctx)))

;;
;; Logging tool/agent thinking/acting
;;

(defn print-indexed-step [action plan step]
  (println (ansi/compose [:bold (format "%s: %s" (name action) step)]))
  (println (ansi/compose [:italic plan])))

(defn print-action [action parameters step]
  (println)
  (println (ansi/compose [:bold "Act: " step]))
  (println (ansi/compose [:bold "- Action: "] [:italic (name action)]))
  (println (ansi/compose [:bold "- Parameters: "] [:italic parameters])))

(defn print-thought [plan content]
  (println)
  (println (ansi/compose [:bold (str plan ":")]))
  (println (ansi/compose [:italic content])))

(defn print-result [result]
  (println)
  (println (ansi/compose [:bold "Agent found the solution: "] result)))

(defn print-too-much-thinking-error [steps]
  (println)
  (println
    (ansi/compose
      [:bold.red
       (format "Agent was thinking for %s steps and failed to find a solution" steps)])))
