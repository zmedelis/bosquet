(ns bosquet.agent.agent
  (:require
    [bosquet.template.read :as template]
    [taoensso.timbre :as timbre]
    [io.aviso.ansi :as ansi]
    [taoensso.timbre.appenders.core :as appenders]))

(timbre/merge-config!
  {:appenders {:println {:enabled? false}
               :spit    (appenders/spit-appender {:fname "bosquet.log"})}})

(def agent-prompt-palette (template/load-palettes "resources/prompt-palette/agent"))

(defn print-indexed-step [action plan step]
  (println (ansi/compose [:bold (format "%s: %s" action step)]))
  (println (ansi/compose [:italic plan])))

(defn print-action [agent parameters step]
  (println (ansi/compose [:bold "Action: " step]))
  (println (ansi/compose [:bold "- Agent: "] [:italic agent]))
  (println (ansi/compose [:bold "- Parameters: "] [:italic parameters])))


(defn print-thought [plan content]
  (println (ansi/compose [:bold (str plan ":")]))
  (println (ansi/compose [:italic content])))


(defprotocol Agent
  (think  [this ctx])
  (act    [this ctx])
  (search [this ctx])
  (lookup [this ctx])
  (finish [this ctx]))
