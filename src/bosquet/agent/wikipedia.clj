(ns bosquet.agent.wikipedia
  (:require
    [bosquet.agent.agent :as a]
    [bosquet.generator :as generator]
    [taoensso.timbre :as timbre]
    [org.httpkit.client :as http]
    [jsonista.core :as j]))


(defn search-wiki [query]
  @(http/request
    {:method       :get
     :url          "https://en.wikipedia.org/w/api.php"
     :query-params {"search"    query
                    "namespace" 0
                    "limit"     3
                    "action"    "opensearch"}}))

(deftype Wikipedia [] a/Agent
         (search [_this query]
           (timbre/info "Searching Wikipedia for" query)
           (generator/complete a/agent-prompt-palette
             {:question query}
             [:react/prompt :thoughts]))
         (lookup [_this query db]
           (println "Looking up Wikipedia for" query))
         (finish [_this]
           (println "Finishing Wikipedia")))

(comment
  (def w (Wikipedia.))
  (a/search w "Author David Chanoff has collaborated with a U.S. Navy admiral who served as the ambassador to the United Kingdom under which President?")
  #__)
