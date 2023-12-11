(ns examples.rag
  (:require
   [hfds-clj.core :as hfds]))


(def fiqa-dataset
  (hfds/load-dataset {:dataset "explodinggradients/fiqa"
                      :config "corpus"
                      :split "corpus"}))

;; 1. Create embeddings
;; 2. Create RAG pipeline
;; 3. Use RAGA to evaluate the quality
