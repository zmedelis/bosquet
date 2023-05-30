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

(deftype Wikipedia [] Agent
  (search [_this query]
    (timbre/info "Searching Wikipedia for" query)
    (generator/complete agent-prompt-palette
      {:question query}
      [:react/prompt :thoughts]))
  (lookup [_this query db]
    (println "Looking up Wikipedia for" query))
  (finish [_this]
    (println "Finishing Wikipedia")))

(comment
  (def w (Wikipedia.))
  (search w "Author David Chanoff has collaborated with a U.S. Navy admiral who served as the ambassador to the United Kingdom under which President?")
  #__)
