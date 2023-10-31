(ns user
  #_{:clj-kondo/ignore [:unused-namespace]}
  (:require
   [bosquet.system :as system]
   [clojure.string :as string]
   [clojure.tools.namespace.repl :refer [set-refresh-dirs]]
   [integrant.core :as ig]
   [integrant.repl :as ir]
   [nextjournal.clerk :as clerk]
   [portal.api :as p]
   [taoensso.timbre :as timbre]))

(ir/set-prep! #(ig/prep system/sys-config))
(set-refresh-dirs "src")

(defn log-output-fn
  [data]
  (let [{:keys [level ?err ?ns-str ?file timestamp_ ?line output-opts]} data
        context  (format "%s %s [%s:%3s]:"
                   (force timestamp_)
                   (-> level name string/upper-case)
                   (or ?ns-str ?file "?") (or ?line "?"))]
    (format
      "%-42s %s%s"
      context
      (if-let [msg-fn (get output-opts :msg-fn timbre/default-output-msg-fn)]
        (msg-fn data) "")
      (if ?err
        ((get output-opts :error-fn timbre/default-output-error-fn) data) ""))))


(timbre/merge-config! {:output-fn log-output-fn
                       :timestamp-opts {:pattern "HH:mm:ss"}})


(defn build-static-docs
  [_]
  (clerk/build! {:paths    ["notebook/getting_started.clj"
                            "notebook/configuration.clj"
                            "notebook/math_generate_code.clj"
                            "notebook/examples/short_memory_prosocial_dialog.clj"
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
