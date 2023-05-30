(ns bosquet.agent.agent
  (:require
   [bosquet.generator :as generator]
   [bosquet.template.read :as template]
   [taoensso.timbre :as timbre]))

(def agent-prompt-palette (template/load-palettes "resources/prompt-palette/agent"))

(defprotocol Agent
  (search [this query])
  (lookup [this query db])
  (finish [this]))
