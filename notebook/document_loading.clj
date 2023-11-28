;; ## Document Loading
;;
;;
;; Bosquet uses [Apache Tika](https://tika.apache.org/) for document parsing. Tika supports various document types:
;; PDF, MS Office, and OpenOffice are but a few in the [complete list](https://tika.apache.org/2.9.1/formats.html)
;; Document reading using Tika is done in the `bosquet.read.document` namespace.

(ns document-loading
  (:require
   [bosquet.read.document :as d]))

;; The `parse` function is wrapping Tika API. It accepts either a file name or an input stream of the document and returns a map containing two keys:
;; * **text** - with the extracted document text
;; * **metadata** - extracted document metadata, containing various entries depending on how the document was created: title, create date, authors, etc. Some of the metadata entries will be conveniently presented using the [Dublin Core](https://en.wikipedia.org/wiki/Dublin_Core) format


;; ### Few examples of document parsing:
;;
;; #### PDF document

^{:nextjournal.clerk/auto-expand-results? true}
(d/parse "data/memory.pdf")

;; #### MS Excel document

^{:nextjournal.clerk/auto-expand-results? true}
(d/parse "data/netflix.xls")
