(ns bosquet.agent.wikipedia
  (:require
    [bosquet.agent.agent :as a]
    [bosquet.generator :as generator]
    [taoensso.timbre :as timbre]
    [org.httpkit.client :as http]
    [jsonista.core :as j]))

(defn search-wiki [query]
  (let [result
        (->> @(http/request
                {:method       :get
                 :url          "https://en.wikipedia.org/w/api.php"
                 :query-params {"search" query
                                "limit"  3
                                "action" "opensearch"}})
          :body
          j/read-value)]
    ;; Wikipedia API returns a vector of 4 items:
    ;; 1. query
    ;; 2. titles of matching articlesA
    ;; 3. short descriptions of matching articles (?)
    ;; 4. links to matching articles
    ;; We only care about the second and last items.
    (mapv vector
      (second result)
      (last result))))

(defn best-match
  "`query` is a string used to search Wikipedia in `search-wiki` call
   `results` is a vector of tuples `[title link]`

  Best match is determined by the following criteria:
  - If there is exact match between `query` and `title`, return it
  - Otherwise return the first result (trusting Wikipedia's search algorithm)"
  [query results]
  (if-let [exact-match (first (filter #(= query (first %)) results))]
    exact-match
    (first results)))

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

  (search-wiki "Fox")

  (def question "Author David Chanoff has collaborated with a U.S. Navy admiral who served as the ambassador to the United Kingdom under which President?")
  (def w (Wikipedia.))
  (a/search w question)

  #__)
