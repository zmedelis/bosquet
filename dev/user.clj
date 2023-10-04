(ns user
  #_{:clj-kondo/ignore [:unused-namespace]}
  (:require [nextjournal.clerk :as clerk]
            [portal.api :as p]))

(defn build-static-docs
  [_]
  (clerk/build! {:paths    ["notebook/getting_started.clj"
                            #_"notebook/user_guide.clj"]
                 :index    "notebook/index.clj"
                 :out-path "docs"}))

(comment
  (def p (p/open))
  (add-tap #'p/submit)

  (clerk/serve! {:watch-paths ["notebook"]})

  (clerk/show! "notebook/getting_started.clj")
  (clerk/show! "notebook/user_guide.clj")
  (clerk/show! "notebook/chat_with_memory.clj")
  (clerk/show! "notebook/text_analyzers.clj")
  (clerk/show! "notebook/wedding_guest_example.clj"))
