(ns bosquet.read.document
  "Document reader wrapping Apache Tika parser
  https://tika.apache.org/

  Tika supports lots of different file formats
  https://tika.apache.org/2.9.1/formats.html

  `parse` function will use Tika capabilities to convert provided
  document data to a map containing `text` and `metadata` fields.

  ```clojure
  (parse (clojure.java.io/input-stream \"data/memory.pdf\"))
  =>
  {:text     \"Memory, reasoning, and categorization: parallels and\ncommon mechanisms\nBrett K. ...\"
   :metadata {:dc:creator \"Brett K. Hayes\"
              :dc:description \"Traditionally, memory, reasoning, and categorization have been \"}
  ```"
  (:require
   [clojure.java.io :as io])
  (:import
   [org.apache.tika.metadata Metadata]
   [org.apache.tika.parser AutoDetectParser]
   [org.apache.tika.sax BodyContentHandler]))

(defn- extract-metadata
  "Convert tika Metadata object into plain map containing only Dublin Core metadata."
  [metadata]
  (reduce (fn [m k]
            (assoc m (keyword k)
                   (if (.isMultiValued metadata k)
                     (into [] (.getValues metadata k))
                     (.get metadata k))))
          {}
          (.names metadata)))

(defn- body-content-handler
  []
  (let [;; BodyContentHandler will process only specified number of characters,
        ;; this is a guard against parsing huge files note, that it is file content chars
        ;; so in cases of binary files it is not your letters
        ;; -1 means no limit
        file-char-limit -1]
    (BodyContentHandler. file-char-limit)))

(defn parse
  "Extract text and metadata from `doc-input-stream`. The stream can contain
  and data of a file formats supported by Tika. File format detection will be
  done automatcaly by Tika.

  Returns a map with
  - `text` field containing document in a plain text format
  - `metadata` Dublin Core defined metadata fields if document has those defined"
  [stream-or-file-name]
  (let [input    (if (string? stream-or-file-name)
                   (io/input-stream stream-or-file-name)
                   stream-or-file-name)
        parser   (AutoDetectParser.)
        handler  (body-content-handler)
        metadata (Metadata.)]
    (.parse parser input handler metadata)
    {:text     (.toString handler)
     :metadata (extract-metadata metadata)}))
