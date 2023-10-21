(ns bosquet.nlp.embeddings
  (:require
   [wkok.openai-clojure.api :as api]))

(defprotocol Embeddings
  (create [_this _text]))

(def ^:private openai-embedding-model "text-embedding-ada-002")

(deftype OpenAIEmbeddings
         [opts]
  Embeddings
  (create [_this text]
    (api/create-embedding {:model openai-embedding-model
                           :input text}
                          opts)))

(comment
  (require '[bosquet.system :as system])
  (require '[bosquet.db.qdrant :as qd])

  (def qd-coll-name "test-embs")
  (qd/delete-collection qd-coll-name)
  (qd/create-collection qd-coll-name qd/embeddings-collection-config)

  (def sys (system/get-service :embedding/openai))
  (def texts ["Hello world"
              "Hello town"
              "Goodmorning fields"
              "Cars are driving on the road"])
  (def embeds (mapv (fn [text]
                      {:data {:text text}
                       :embedding
                       (-> sys (create text) :data first :embedding)})
                    texts))

  (def query (-> sys (create "Cars in town") :data first :embedding))

  (qd/add-docs qd-coll-name embeds)

  (qd/search qd-coll-name query 2)

  #__)
