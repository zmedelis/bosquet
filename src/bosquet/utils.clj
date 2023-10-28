(ns bosquet.utils
  (:require
   [hato.client :as hc]
   [jsonista.core :as j])
  (:import
   [java.util UUID]))

(defn uuid []
  (UUID/randomUUID))

(def ^:private datasets-server "https://datasets-server.huggingface.co/rows")

(defn hf-dataset [params]
  (map :row
       (-> (hc/get datasets-server {:query-params params})
           :body
           (j/read-value j/keyword-keys-object-mapper)
           :rows)))

(comment
  (hf-dataset
   {:dataset "stingning/ultrachat"
    :split   "train"
    :config  "default"
    :offset  0
    :length  10}))
