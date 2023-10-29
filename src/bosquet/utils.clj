(ns bosquet.utils
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [hato.client :as hc]
   [jsonista.core :as j]
   [taoensso.timbre :as timbre]
   [me.flowthing.pp :as pp])
  (:import
   [java.util UUID]))

(defn uuid []
  (UUID/randomUUID))

(defn pp-str
  [x]
  (with-out-str (pp/pprint x)))

(def ^:private datasets-server "https://datasets-server.huggingface.co/rows")

;; TODO. Probably a good candidate for independent lib

(defn- write-ds
  [ds-file ds]
  (io/make-parents ds-file)
  (spit ds-file (pp-str ds)))

(defn- ds-file-name
  [dataset cache-dir]
  (io/file (str cache-dir "/" (string/replace dataset "/" "_") ".edn")))

(defn- fetch-hfds [{:keys [dataset offset] :as hf-params}]
  (timbre/infof "Fetching HuggingFace dataset %s (offset=%s)" dataset offset)
  (-> (hc/get datasets-server {:query-params hf-params})
      :body
      (j/read-value j/keyword-keys-object-mapper)))

(defn download-ds [{:keys [dataset offset length] :as params}
                   {:hfds/keys [use-cache cache-dir]
                    :or        {use-cache false cache-dir ".cache/hugging-face-datasets"}}]
  (timbre/infof "Downloading page 1")
  (let [{:keys [num_rows_total] :as first-page} (fetch-hfds params)]
    (timbre/info "Total records: " num_rows_total)
    (write-ds (str (ds-file-name dataset cache-dir) ".page1") first-page)
    (loop [page 1]
      (if (> num_rows_total (+ offset (* page length)))
        (do
          (timbre/infof "Downloading page %s" (inc page))
          (write-ds
           (str (ds-file-name dataset cache-dir) ".page" (inc page))
           (fetch-hfds (assoc params :offset (+ offset (* page length)))))
          (recur (inc page)))
        (timbre/info "Done downloading")))))

(defn- transform-hfds
  [hfds-raw]
  (mapv :row (:rows hfds-raw)))

(defn hf-dataset
  ([hf-params] (hf-dataset hf-params nil))
  ([{:keys [dataset] :as hf-params}
    {:hfds/keys [use-cache cache-dir]
     :or        {use-cache false cache-dir ".cache/hugging-face-datasets"}}]
   (transform-hfds
    (if use-cache
      (let [cached-ds-file (ds-file-name dataset cache-dir)]
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
  (download-ds
   {:dataset "allenai/prosocial-dialog"
    :split   "train"
    :config  "default"
    :offset  120000
    :length  100}
   {:hfds/use-cache true})

  (hf-dataset
   {:dataset "stingning/ultrachat"
    :split   "train"
    :config  "default"
    :offset  2000000
    :length  10}))
