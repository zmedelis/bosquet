(ns examples.prosocial-dialog
  (:require
   [bosquet.memory.retrieval :as r]
   [bosquet.system :as system]
   [bosquet.dataset.huggingface :as hfds]
   [bosquet.wkk :as wkk]))

;; https://huggingface.co/datasets/allenai/prosocial-dialog

(comment
  (hfds/download-ds
   {:dataset "allenai/prosocial-dialog"
    :split   "train"
    :config  "default"
    :offset  0
    :length  100}
   {:hfds/use-cache true
    :hfds/record-limit 1000}))


(def prosocial-dialog-dataset
  (hfds/load-ds "allenai/prosocial-dialog"))

(def first-response-subset
  (filter #(zero? (:response_id %))
    prosocial-dialog-dataset))

(def memory (system/get-memory wkk/long-term-embeddings-memory))

(def mem-config
  {wkk/memory-system     memory
   wkk/recall-function   r/recall-cue
   r/memory-tokens-limit 500
   r/memory-content-fn   :content})

(defn remember-dialogs
  [memory dialogs]
  (.remember memory
    (mapv (fn [{:keys [response context rots]}]
            {:text    response
             :payload {:context context
                       :rots    rots}})
      dialogs)
    {:collection-name "prosocial-dialog"}))

(comment
  (remember-dialogs
    memory
    (take 3 first-response-subset))
  #__)
