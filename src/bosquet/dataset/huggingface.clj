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
   [clojure.edn :as edn]
   [clojure.string :as string]
   [hato.client :as hc]
   [jsonista.core :as j]
   [taoensso.timbre :as timbre]
   [net.modulolotus.truegrit :as tg]))

;; TODO. Probably a good candidate for independent lib

(def ^:private datasets-server "https://datasets-server.huggingface.co/rows")

(def ^:private default-cache-dir ".cache/hugging-face-datasets")

(defn- write-ds
  [ds-file ds]
  (io/make-parents ds-file)
  (spit ds-file (utils/pp-str ds)))

(defn- ds-dir-name
  [cache-dir dataset]
  (str cache-dir "/" dataset))

(defn- ds-file
  ([cache-dir dataset] (ds-file cache-dir dataset 1))
  ([cache-dir dataset part-nr]
   (io/file
    (format "%s/part-%04d.edn"
            (ds-dir-name cache-dir dataset)
            part-nr))))

(defn- fetch-dataset*
  [{:keys [dataset offset] :as hf-params}]
  (timbre/infof "Fetching HuggingFace dataset %s (offset=%s)" dataset offset)
  (-> (hc/get datasets-server {:query-params hf-params})
      :body
      (j/read-value j/keyword-keys-object-mapper)))

(defn- fetch-dataset [hf-params]
  (let [fetch (-> (fn [] (fetch-dataset* hf-params))
                  (tg/with-time-limiter {:timeout-duration 5000})
                  (tg/with-retry
                    {:name "hf-retry"
                     :max-attempts 5
                     :wait-duration 1000
                     :retry-on-result nil?}))]
    (fetch)))

(defn download-ds
  "Download `dataset` from HuggingFace. Dataset name is usually specified
  in HuggingFace dataset webpage. Usually in a form of `org-name/ds-name`

  First argument is a map specifying HuggingFace HTTP call parame "
  [{:keys [dataset offset length] :as params}
   {:hfds/keys [use-cache cache-dir record-limit]
    :or        {use-cache false cache-dir ".cache/hugging-face-datasets"}}]
  (timbre/infof "Downloading page 1")
  (let [{:keys [num_rows_total] :as first-page} (fetch-dataset params)
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
             (fetch-dataset (assoc params :offset (+ offset (* page length)))))
            (recur (inc page)))
          (timbre/info "Done downloading"))))))

(defn load-ds
  ([dataset]
   (load-ds default-cache-dir dataset nil))
  ([ds-dir dataset record-limit]
   ;; TODO read up to record-limit
   (let [xf (comp
             (filter #(.isFile %))
             (mapcat #(-> % slurp edn/read-string :rows))
             #_(mapcat :rows)
             (map :row))]
     (sequence xf
               (file-seq (io/file (ds-dir-name ds-dir dataset)))))))

(defn- transform-hfds
  [hfds-raw]
  (mapv :row (:rows hfds-raw)))

#_(defn hf-dataset
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

  (require '[criterium.core :as b])
  (def ds-dir default-cache-dir)
  (def dataset "allenai/prosocial-dialog")
  (def ds (load-ds default-cache-dir dataset 1000))

;; Evaluation count : 6 in 6 samples of 1 calls.
;;              Execution time mean : 4.543945 sec
;;     Execution time std-deviation : 40.544732 ms
;;    Execution time lower quantile : 4.485091 sec ( 2.5%)
;;    Execution time upper quantile : 4.589002 sec (97.5%)
;;                    Overhead used : 2.056129 ns

  (download-ds
   {:dataset "allenai/prosocial-dialog"
    :split   "train"
    :config  "default"
    :offset  0
    :length  100}
   {:hfds/use-cache true
    #_#_:hfds/record-limit 1500})

  #_(hf-dataset
     {:dataset "stingning/ultrachat"
      :split   "train"
      :config  "default"
      :offset  2000000
      :length  10}))