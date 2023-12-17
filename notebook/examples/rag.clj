(ns examples.rag
  (:require
   [bosquet.llm.openai-tokens :as tokenizer]
   [bosquet.system :as system]
   [bosquet.wkk :as wkk]
   [clojure.string :as string]
   [hfds-clj.core :as hfds]))


(def fiqa-dataset
  (hfds/load-dataset {:dataset "explodinggradients/fiqa"
                      :config "corpus"
                      :split "corpus"}))

(defn ds->text
  [ds]
  (string/join "\n" (map :doc ds)))

(def ds-token-count (tokenizer/token-count (ds->text fiqa-dataset) :text-embedding-ada-002))

;; 1. Create embeddings
;; 2. Create RAG pipeline
;; 3. Use RAGA to evaluate the quality

(defn ds->text
  [ds]
  (tokenizer/embeddings-price-estimate
   (string/join "\n" (map :doc ds))))

(def mem (system/get-memory wkk/long-term-embeddings-memory))
