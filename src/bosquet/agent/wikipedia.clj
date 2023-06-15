(ns bosquet.agent.wikipedia
  (:require
    [bosquet.agent.tool :as t]
    [jsonista.core :as j]
    [org.httpkit.client :as http]))

(defn- read-json [json]
  (j/read-value json (j/object-mapper {:decode-key-fn true})))

(defn call-wiki [params]
  (->> @(http/request
          {:method       :get
           :url          "https://en.wikipedia.org/w/api.php"
           :query-params params})
    :body read-json))

(defn search-wiki-titles
  "Searh Wikipedia for `query` and return a vector of tuples `[title link]`"
  [query]
  ;; Wikipedia API returns a vector of 4 items:
  ;; 1. query
  ;; 2. titles of matching articlesA
  ;; 3. short descriptions of matching articles (?)
  ;; 4. links to matching articles
  ;; We only care about the second and last items.
  (second (call-wiki {"search" query
                      "limit"  3
                      "action" "opensearch"})))

(defn fetch-page [title]
  (-> (call-wiki
        {"action"      "query"
         "titles"      title
         "prop" "extracts"
         ;; Numer of sentences to return in the extract
         "exsentences" 5
         ;; Return plain text instead of HTML
         "explaintext" "yes"
         "exintro"     "yes"
         "format"      "json"})
    :query :pages vec first second :extract))

(defn best-match
  "`query` is a string used to search Wikipedia in `search-wiki` call
   `results` is a vector of tuples `[title link]`

  Best match is determined by the following criteria:
  - If there is exact match between `query` and `title`, return it
  - Otherwise return the first result (trusting Wikipedia's search algorithm)"
  [query results]
  (if-let [exact-match (get (set results) query)]
    exact-match
    (first results)))

(defn extract-page-content [query]
  (fetch-page
    (best-match
      query
      (search-wiki-titles query))))

(deftype Wikipedia
    [] t/Tool
    (my-name [_this] "Wikipedia")
    (search [_this {query :parameters}]
      (extract-page-content query))
    (lookup [_this _ctx])
    (finish [_this {query :parameters}]
      query))

(comment
  (def w (Wikipedia.))
  (t/my-name w)
  (t/search w {:parameters "Fox"})
  #__)
