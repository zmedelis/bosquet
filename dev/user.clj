(ns user
  (:require [nextjournal.clerk :as clerk]))

;; start Clerk's built-in webserver on the default port 7777, opening the browser when done
(clerk/serve! {:browse? true})

;; or let Clerk watch the given `:paths` for changes
#_(clerk/serve! {:watch-paths ["notebook"]})

(clerk/show! "notebook/use_guide.clj")
