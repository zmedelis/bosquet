(ns bosquet.nlp.embeddings
  (:require
   [bosquet.memory.encoding :as encoding]
   [wkok.openai-clojure.api :as api]))

(def ^:private openai-embedding-model "text-embedding-ada-002")

(defn oai-embeddings [text opts]
  (api/create-embedding {:model openai-embedding-model
                         :input text}
                        opts))

(deftype
 OpenAIEmbeddings
 [opts]
  encoding/Encoder
  (encode [_this text] (oai-embeddings text opts)))

(comment
  (require '[bosquet.system :as system])
  (require '[bosquet.db.qdrant :as qd])

  (def embeddings-collection-config
    {:vectors-size     1536
     :vectors-distance :Dot})

  (def qd-coll-name "test-embs")
  (qd/delete-collection qd-coll-name)
  (qd/create-collection qd-coll-name embeddings-collection-config)

  (def oai-emb (system/get-service :embedding/openai))
  (def texts ["Hello world"
              "Hello town"
              "Goodmorning fields"
              "Cars are driving on the road"])
  (def embeds (mapv (fn [text]
                      {:payload {:text text}
                       :embedding
                       (-> oai-emb (.encode text) :data first :embedding)})
                texts))


  (qd/add-docs qd-coll-name embeds)

  (def query (-> oai-emb (.encode "Cars in town") :data first :embedding))

  (qd/search qd-coll-name query 2)

  ;; Same but via Memory component

  (def qd (system/get-service :db/qdrant))

  (.create qd qd-coll-name)
  (.add qd qd-coll-name embeds)
  (.search qd qd-coll-name query 2)



  #__)
