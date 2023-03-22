(ns test
  (:require
   [bosquet.splitter :as splitter]
   [bosquet.generator :as gen]
   [nextjournal.clerk :as clerk]))
(comment
  (clerk/serve! {:browse? true}))




(def text (slurp "https://raw.githubusercontent.com/scicloj/scicloj.ml.smile/main/LICENSE"))

(def what-template
  "
{{text}}

What is the name of the licence ?
{% llm-generate  model=testtextdavanci003 impl=azure % }")

;;  split the text in pieces
(def splits
  (splitter/split-max-tokens text 1000 splitter/heuristic-gpt-token-count-fn))


;; apply template to all pieces
(def results
  (mapv
   #(gen/complete-template what-template {:text %})
   splits))

;;  template to summarize the result for all pieces
(def summarise-all-template
  "
{% for t in results %}
  * {{t}}
{% endfor %}

Please summarize the above.
{% llm-generate  model=testtextdavanci003 impl=azure % }
")

;; apply summarise-all tamplate to get single answer
(def overall-summary
  (gen/complete-template summarise-all-template {:results (map first results)}))
