(ns bosquet.dataset.huggingface
  "Dataset fetching and storing from HuggingFace.

  HF Datasets provide rich functionality
  https://huggingface.co/docs/datasets/index

  Replicating it all would be a sizeable effort, here I
  have only functionality needed by Bosquet.
  TODO extract it to a separate OSS lib (even if it only covers
  basics of HF DS functionality)"
  (:require
   [bosquet.utils :as utils]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [hato.client :as hc]
   [jsonista.core :as j]
   [taoensso.timbre :as timbre]))

;; TODO. Probably a good candidate for independent lib

(def ^:private datasets-server "https://datasets-server.huggingface.co/rows")

(def ^:private default-cache-dir ".cache/hugging-face-datasets")

(defn- write-ds
  [ds-file ds]
  (io/make-parents ds-file)
  (spit ds-file (utils/pp-str ds)))

(defn- ds-dir-name [cache-dir dataset]
  (format "%s/%s"
          cache-dir
          (string/replace dataset "/" "_")))

(defn- ds-file
  ([cache-dir dataset] (ds-file cache-dir dataset 1))
  ([cache-dir dataset part-nr]
   (io/file
    (format "%s/part-%04d.edn"
            (ds-dir-name cache-dir dataset)
            part-nr))))

(defn- fetch-hfds [{:keys [dataset offset] :as hf-params}]
  (timbre/infof "Fetching HuggingFace dataset %s (offset=%s)" dataset offset)
  (-> (hc/get datasets-server {:query-params hf-params})
      :body
      (j/read-value j/keyword-keys-object-mapper)))

(defn download-ds [{:keys [dataset offset length] :as params}
                   {:hfds/keys [use-cache cache-dir record-limit]
                    :or        {use-cache false cache-dir ".cache/hugging-face-datasets"}}]
  (timbre/infof "Downloading page 1")
  (let [{:keys [num_rows_total] :as first-page} (fetch-hfds params)
        record-limit (or record-limit num_rows_total)]
    (timbre/info "Total records: " num_rows_total)
    (write-ds (ds-file cache-dir dataset 1) first-page)
    (loop [page 1]
      (let [from-offset (+ offset (* page length))]
        (if (and
             (> num_rows_total from-offset)
             (> record-limit from-offset))
          (do
            (timbre/infof "Downloading page %s" (inc page))
            (write-ds
             (ds-file cache-dir dataset (inc page))
             (fetch-hfds (assoc params :offset (+ offset (* page length)))))
            (recur (inc page)))
          (timbre/info "Done downloading"))))))

(defn load-ds
  ([dataset]
   (load-ds dataset default-cache-dir nil))
  ([dataset ds-dir record-limit]
   ;; TODO read up to record-limit
   (let [xf (comp
             (filter #(.isFile %))
             (map #(-> % slurp read-string))
             (mapcat :rows)
             (map :row))]
     (into [] xf
           (file-seq (io/file (ds-dir-name ds-dir dataset)))))))

(defn- transform-hfds
  [hfds-raw]
  (mapv :row (:rows hfds-raw)))

(defn hf-dataset
  ([hf-params] (hf-dataset hf-params nil))
  ([{:keys [dataset] :as hf-params}
    {:hfds/keys [use-cache cache-dir]
     :or        {use-cache false cache-dir default-cache-dir}}]
   (transform-hfds
    (if use-cache
      (let [cached-ds-file (ds-file dataset cache-dir)]
        (if (.exists cached-ds-file)
           ;; use existing file, do not fetch from HF
          (with-open [rdr (io/reader cached-ds-file)]
            (timbre/infof "Getting HuggingFace dataset %s from cache" cached-ds-file)
            (read-string (slurp rdr)))
           ;; fetch DS from HF and persist it
          (let [ds (fetch-hfds hf-params)]
            (write-ds cached-ds-file ds)
            ds)))
      (fetch-hfds hf-params)))))

(comment
  (load-ds default-cache-dir "allenai/prosocial-dialog" 1000)

  (download-ds
   {:dataset "allenai/prosocial-dialog"
    :split   "train"
    :config  "default"
    :offset  0
    :length  100}
   {:hfds/use-cache true
    :hfds/record-limit 1000})

  (hf-dataset
   {:dataset "stingning/ultrachat"
    :split   "train"
    :config  "default"
    :offset  2000000
    :length  10}))
