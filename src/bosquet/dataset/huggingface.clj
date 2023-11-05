(ns bosquet.dataset.huggingface
  "Dataset fetching and storing from HuggingFace.

  HF Datasets provide rich functionality
  https://huggingface.co/docs/datasets/index

  Replicating it all would be a sizeable effort, here I
  have only functionality needed by Bosquet."
  (:require
   [bosquet.utils :as utils]
   [progrock.core :as pr]
   [clojure.java.io :as io]
   [clojure.edn :as edn]
   [hato.client :as hc]
   [jsonista.core :as j]
   [taoensso.timbre :as timbre]
   [net.modulolotus.truegrit :as tg]))

(def ^:private datasets-server "https://datasets-server.huggingface.co/rows")

(def ^:private default-cache-dir (str (System/getProperty "user.home") "/.cache/hfds-clj"))

(defn- write-ds
  [ds-file ds]
  (io/make-parents ds-file)
  (spit ds-file (utils/pp-str ds)))

(defn- ds-dir-name
  [cache-dir split dataset]
  (str cache-dir "/" dataset "/" split))

(defn- ds-cached?
  [cache-dir split dataset]
  (.exists (io/file (ds-dir-name cache-dir split dataset))))

(defn- ds-file
  ([cache-dir split dataset] (ds-file cache-dir split dataset 1))
  ([cache-dir split dataset part-nr]
   (io/file
    (format "%s/part-%04d.edn"
            (ds-dir-name cache-dir split dataset)
            part-nr))))

(defn- fetch-dataset*
  [ds-params]
  (-> (hc/get datasets-server {:query-params ds-params})
    :body
    (j/read-value j/keyword-keys-object-mapper)))

(defn- fetch-dataset
  "Fetch dataset with True Grit backed resilience. It will retry fetching on HF errors."
  [hf-params]
  (let [fetch (-> (fn [] (fetch-dataset* hf-params))
                (tg/with-time-limiter {:timeout-duration 5000})
                (tg/with-retry
                  {:name            "hf-retry"
                   :max-attempts    5
                   :wait-duration   1000
                   :retry-on-result nil?}))]
    (fetch)))

(defn download-ds
  [{:keys [dataset offset length split]
    :as   params}
   {:hfds/keys [cache-dir limit]
    :or        {cache-dir default-cache-dir}}]
  (timbre/infof "Downloading %s:%s" dataset split)
  (letfn [(log-progress [bar page]
            (pr/print (pr/tick bar (* page length))))]
    (let [{:keys [num_rows_total] :as first-page} (fetch-dataset params)
          record-limit                            (or limit num_rows_total)
          bar                                     (pr/progress-bar record-limit)]
      (log-progress bar 1)
      (write-ds (ds-file cache-dir split dataset 1) first-page)
      (loop [page 1]
        (let [from-offset (+ offset (* page length))]
          (if (and
                (> num_rows_total from-offset)
                (> record-limit from-offset))
            (do
              (log-progress bar (inc page))
              (write-ds
                (ds-file cache-dir split dataset (inc page))
                (fetch-dataset (assoc params :offset (+ offset (* page length)))))
              (recur (inc page)))
            (timbre/info "\nDone downloading 🤗")))))))

(defn read-ds
  [{:keys [dataset split]}
   {:hfds/keys [limit cache-dir]
    :or        {cache-dir default-cache-dir}}]
  (timbre/infof "Loading '%s:%s' from cache" dataset split)
  (let [xf (comp
            (filter #(.isFile %))
            (mapcat #(-> % slurp edn/read-string :rows))
            (map :row))
        ds   (sequence xf
                       (file-seq (io/file (ds-dir-name cache-dir split dataset))))]
    (if limit
      (take limit ds)
      ds)))

(defn load-dataset
  "Download `dataset` from HuggingFace. Dataset name is usually specified
  in HuggingFace dataset webpage. Usually in a form of `org-name/ds-name`

  First argument is a map specifying HuggingFace HTTP call parameters and
  is used as is for HF REST API HTTP calls.

  Second argument is a map specifying how to read the ds."
  [{:keys [dataset split config offset length]
    :or   {split  "train"
           config "default"
           offset 0
           length 100}}
   {:hfds/keys [cache-dir download-mode]
    :or        {download-mode :reuse-dataset-if-exists
                cache-dir     default-cache-dir}
    :as        read-params}]
  (let [ds-params {:dataset dataset
                   :split   split
                   :config  config
                   :offset  offset
                   :length  length}]
    (if (and (= :reuse-dataset-if-exists download-mode)
          (ds-cached? cache-dir split dataset))
      (read-ds ds-params read-params)
      (do
        (download-ds ds-params read-params)
        (read-ds ds-params read-params)))))

(comment
  (def prosoc-ds (load-dataset
                   {:dataset "allenai/prosocial-dialog"
                    :split   "train"
                    :config  "default"
                    :offset  0
                    :length  100} {}))
  (def prosoc-ds (load-dataset {:dataset "stingning/ultrachat"}
                   {:hfds/download-mode :force-download
                    :hfds/limit         1000}))
  #__)
