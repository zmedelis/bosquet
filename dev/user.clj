(ns user
  #_{:clj-kondo/ignore [:unused-namespace]}
  (:require
   [bosquet.system :as system]
   [integrant.core :as ig]
   [integrant.repl :as ir]
   [nextjournal.clerk :as clerk]
   [portal.api :as p]
   [taoensso.timbre :as timbre]))

(ir/set-prep! #(ig/prep system/sys-config))

#_(timbre/merge-config!
    {:appenders
     {:println
      {:enabled? true
       :fn (fn [{:keys [level instant output_ ?line ?ns-str] :as data}]
             (printf "%s | %s | (%s:%s) | %s\n"
               (name level) instant ?ns-str ?line (force output_)))}}})

(defn build-static-docs
  [_]
  (clerk/build! {:paths    ["notebook/getting_started.clj"
                            "notebook/configuration.clj"
                            "notebook/math_generate_code.clj"
                            "notebook/papers/chain_of_density.clj"
                            "notebook/papers/chain_of_verification.clj"]
                 :index    "notebook/index.clj"
                 :out-path "docs"}))

(comment
  (def p (p/open))
  (add-tap #'p/submit)

  ;; integrant restart
  (ir/go)
  (ir/reset)

  (clerk/serve! {:watch-paths ["notebook"]})

  (clerk/serve! {:browse? false})

  (clerk/show! "notebook/getting_started.clj")
  (clerk/show! "notebook/papers/chain_of_verification.clj")
  (clerk/show! "notebook/configuration.clj")
  (clerk/show! "notebook/user_guide.clj")
  (clerk/show! "notebook/chat_with_memory.clj")
  (clerk/show! "notebook/text_analyzers.clj")
  (clerk/show! "notebook/wedding_guest_example.clj"))
