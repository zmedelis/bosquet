(ns user
  #_{:clj-kondo/ignore [:unused-namespace]}
  (:require
   [clojure.string :as string]
   [nextjournal.clerk :as clerk]
   [portal.api :as p]
   [taoensso.timbre :as timbre]))

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
  (clerk/build! {:paths    ["notebook/user_guide.clj"
                            "notebook/configuration.clj"
                            "notebook/text_splitting.clj"
                            "notebook/document_loading.clj"
                            "notebook/using_llms.clj"
                            "notebook/observability.clj"
                            "notebook/math_generate_code.clj"
                            "notebook/memory_prosocial_dialog.clj"
                            "notebook/papers/chain_of_density.clj"
                            "notebook/papers/chain_of_verification.clj"]
                 :index    "notebook/index.clj"
                 :out-path "docs"}))

(defn open-portal []
  (p/open)
  (add-tap #'p/submit))


(defn clear-portal []
  (p/clear))

(comment
  (open-portal)

  (clerk/serve! {:watch-paths ["notebook"]})

  (clerk/serve! {:browse? false})

  (clerk/show! "notebook/getting_started.clj")
  (clerk/show! "notebook/text_splitting.clj")
  (clerk/show! "notebook/document_loading.clj")
  (clerk/show! "notebook/using_llms.clj")
  (clerk/show! "notebook/examples/short_memory_prosocial_dialog.clj")
  (clerk/show! "notebook/papers/chain_of_verification.clj")
  (clerk/show! "notebook/configuration.clj")
  (clerk/show! "notebook/user_guide.clj")
  (clerk/show! "notebook/chat_with_memory.clj")
  (clerk/show! "notebook/text_analyzers.clj")
  (clerk/show! "notebook/wedding_guest_example.clj"))
