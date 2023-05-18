(ns user
  #_{:clj-kondo/ignore [:unused-namespace]}
  (:require [nextjournal.clerk :as clerk]
            [bosquet.generator :as bg]))

;; start Clerk's built-in webserver on the default port 7777, opening the browser when done
#_(clerk/serve! {:browse? true})

;; or let Clerk watch the given `:paths` for changes
(clerk/serve! {:watch-paths ["notebook"]})

(comment
  (clerk/show! "notebook/use_guide.clj")
  (clerk/show! "notebook/text_analyzers.clj")
  (clerk/show! "notebook/wedding_guest_example.clj"))
