(ns bosquet.agent.agent)

(defprotocol Agent
  (search [this query])
  (lookup [this query])
  (finish [this]))

(deftype Wikipedia [] Agent
  (search [_this query]
    (println "Searching Wikipedia for" query))
  (lookup [_this query]
    (println "Looking up Wikipedia for" query))
  (finish [_this]
    (println "Finishing Wikipedia")))

(comment
  (def w (Wikipedia.))
  (search w "Fox")
  #__)
