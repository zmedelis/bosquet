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

(def ^:private datasets-server "https://datasets-server.huggingface.co/rows")

;; TODO. Probably a good candidate for independent lib

(defn- fetch-hfds [{:keys [dataset] :as hf-params}]
  (timbre/infof "Fetching HuggingFace dataset %s" dataset)
  (mapv :row
        (-> (hc/get datasets-server {:query-params hf-params})
            :body
            (j/read-value j/keyword-keys-object-mapper)
            :rows)))

(defn pp-str
  [x]
  (with-out-str (pp/pprint x)))

(defn hf-dataset
  ([hf-params] (hf-dataset hf-params nil))
  ([{:keys [dataset] :as hf-params}
    {:hfds/keys [use-cache cache-dir]
     :or        {use-cache false cache-dir ".cache/hugging-face-datasets"}}]
   (if use-cache
     (let [cached-ds-file (io/file (str cache-dir "/" (string/replace dataset "/" "_") ".edn"))]
       (if (.exists cached-ds-file)
         ;; use existing file, do not fetch from HF
         (with-open [rdr (io/reader cached-ds-file)]
           (timbre/infof "Getting HuggingFace dataset %s from cache" cached-ds-file)
           (read-string (slurp rdr)))
         ;; fetch DS from HF and persist it
         (let [ds (fetch-hfds hf-params)]
           (io/make-parents cached-ds-file)
           (spit cached-ds-file (pp-str ds))
           ds)))
     (fetch-hfds hf-params))))

(comment
  (hf-dataset
   {:dataset "allenai/prosocial-dialog"
    :split   "train"
    :config  "default"
    :offset  0
    :length  10}
   {:hfds/use-cache true})

  (hf-dataset
   {:dataset "stingning/ultrachat"
    :split   "train"
    :config  "default"
    :offset  0
    :length  10}))
