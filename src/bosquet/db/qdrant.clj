(ns bosquet.db.qdrant
  (:require
   [bosquet.db.vector-db :as vdb]
   [bosquet.utils :as utils]
   [hato.client :as hc]
   [jsonista.core :as j]))

(def embeddings-collection-config
  {:vectors {:size     1536
             :distance :Dot}})

(def ^:private qdrant-endpoint
  "http://localhost:6333")

(defn create-collection
  "Create a collection with `name` and `config`"
  [collection-name config]
  (hc/put (str  qdrant-endpoint "/collections/" collection-name)
          {:content-type :json
           :body         (j/write-value-as-string config)}))

(defn delete-collection
  [collection-name]
  (hc/delete (str qdrant-endpoint "/collections/" collection-name)))

(defn add-docs
  [collection-name data]
  (let [points {:points (mapv (fn [{:keys [data embedding]}]
                                {:id      (utils/uuid)
                                 :vector  embedding
                                 :payload data})
                              data)}]
    (hc/put (format "%s/collections/%s/points?wait=true"
                    qdrant-endpoint collection-name)
            {:content-type :json
             :body         (j/write-value-as-string points)})))

(defn search
  ([collection-name embeds-vector]
   (search collection-name embeds-vector 3))
  ([collection-name embeds-vector top-n]
   (-> (format "%s/collections/%s/points/search" qdrant-endpoint collection-name)
       (hc/post
        {:content-type :json
         :body         (j/write-value-as-string
                        {:vector embeds-vector
                         :top    top-n
                         :with_payload true})})
       :body
       j/read-value)))

(deftype Qdrant
         [opts]
  vdb/VectorDB
  (create [_this collection-name]
    (create-collection collection-name

                       (merge)))
  (delete [_this _collection-name])
  (add [_this _collection-name _docs])
  (search [_this _query _limit]))
