(ns user
  #_{:clj-kondo/ignore [:unused-namespace]}
  (:require [nextjournal.clerk :as clerk]))

(comment
  (clerk/serve! {:watch-paths ["notebook"]})

  (clerk/show! "notebook/user_guide.clj")
  (clerk/show! "notebook/text_analyzers.clj")
  (clerk/show! "notebook/wedding_guest_example.clj"))
