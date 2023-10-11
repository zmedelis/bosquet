(ns bosquet.memory.weaviate
  (:import [io.weaviate.client Config WeaviateClient]
           [io.weaviate.client.v1.graphql.query.argument NearTextArgument]))

(defn ->client []
  (WeaviateClient. (Config. "http" "localhost:8080")))

(defn status [client]
  (let [meta (-> client .misc .metaGetter .run)]
    (if (.getError meta)
      (doseq [msg (-> meta .getError .getMessages vec)]
        (printf "Error: %s\n" (.getMessage msg)))
      (let [res (.getResult meta) ]
        (printf "meta.hostname: %s\n" (.getHostname res))
        (printf "meta.version: %s\n" (.getVersion res))
        (printf "meta.modules: %s\n" (.getModules res))))))

(defn add [client]
  (-> client
    .data
    .creator
    (.withClassName "JeopardyQuestion")
    (.withProperties
      (hash-map
        "question" "This vector DB is OSS and supports automatic property type inference on import"
        "answer" "Weaviate"))
    (.run)))

(defn search [client q]
  (-> client
    (.graphQL)
    (.get)
    (.withClassName "JeopardyQuestion")
    (.withNearText
      (-> (NearTextArgument/builder)
        (.concepts [q])
        (.certainty 0.8)
        (.build)))
    (.withFields "question answer _additional { distance }")
    (.run)))


(comment
  (def c (->client))
  (status c)
  (add c)
  (search c "DB OSS")
  #__)
