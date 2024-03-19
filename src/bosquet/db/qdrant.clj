(ns bosquet.db.qdrant
  (:require
   [bosquet.db.vector-db :as vdb]
   [bosquet.env :as env]
   [clojure.walk :as walk]
   [hato.client :as hc]
   [jsonista.core :as j]
   [bosquet.utils :as u]))


(defn- collections-path
  "URL endpoint for collection with the `name` operations."
  [{:keys [api-endpoint]} {:keys [collection-name]}]
  (str  api-endpoint "/collections/" collection-name))


(defn- points-path
  "URL endpoint for collection with the `points` operations."
  [{:keys [api-endpoint]} {:keys [collection-name]}]
  (format "%s/collections/%s/points?wait=true"
          api-endpoint collection-name))


(defn- search-path
  "URL endpoint for collection with the `points` operations."
  [{:keys [api-endpoint]} {:keys [collection-name]}]
  (format "%s/collections/%s/points/search" api-endpoint collection-name))


(defn collection-info
  [opts params]
  (let [{:keys [body status]} (hc/get (collections-path opts params)
                                      {:throw-exceptions? false})]
    (condp = status
      200 (-> body (j/read-value j/keyword-keys-object-mapper) :result)
      404 nil)))


(defn create-collection
  "Create a collection with `name` and `config`"
  [opts params]
  (hc/put (collections-path opts params)
          {:content-type :json
           :body         (j/write-value-as-string
                          {:vectors (u/snake-case (dissoc opts :api-endpoint))})}))


(defn delete-collection
  [opts params]
  (hc/delete opts params))


(defn add-docs
  "Add docs to the `collection-name` if collection does not exist, create it."
  [opts params data]
  (when-not (collection-info opts params)
    (create-collection opts params))

  (let [points {:points (mapv (fn [{:keys [payload embedding]}]
                                {:id      (u/uuid)
                                 :vector  embedding
                                 :payload payload})
                              data)}]
    (hc/put (points opts params)
            {:content-type :json
             :body         (j/write-value-as-string points)})))


(defn search
  ([opts params embeds-vector]
   (search opts params embeds-vector nil))
  ([opts params embeds-vector {:keys [limit]
                               :or   {limit 3}}]
   (let [res (-> (search-path opts params)
                 (hc/post
                  {:content-type :json
                   :body         (j/write-value-as-string
                                  {:vector       embeds-vector
                                   :top          limit
                                   :with_payload true})})
                 :body u/read-json :result)]
     (map #(select-keys % [:id :score :payload]) res))))

(deftype Qdrant
    [params]
    vdb/VectorDB

    (create [_this]
      (create-collection (env/val :qdrant) params))

    (delete [_this]
      (delete-collection (env/val :qdrant) params))

    (add [_this docs]
      (add-docs (env/val :qdrant) params docs))

    (search [_this embeddings search-opts]
      (search (env/val :qdrant) params embeddings search-opts)))
