(ns user
  #_{:clj-kondo/ignore [:unused-namespace]}
  (:require [nextjournal.clerk :as clerk]
            [portal.api :as p]))

(comment
  (def p (p/open))
  (add-tap #'p/submit)

  (clerk/serve! {:watch-paths ["notebook"]})

  (clerk/show! "notebook/user_guide.clj")
  (clerk/show! "notebook/text_analyzers.clj")
  (clerk/show! "notebook/wedding_guest_example.clj"))
