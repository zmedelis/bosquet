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

(defn print-action [agent parameters]
  (println (ansi/compose [:bold "Action!"]))
  (println (ansi/compose [:bold "\tAgent:\t"] [:italic agent]))
  (println (ansi/compose [:bold "\tParameters:\t"] [:italic parameters])))

(defprotocol Agent
  (plan   [this query])
  (search [this query])
  (lookup [this query db])
  (finish [this]))
