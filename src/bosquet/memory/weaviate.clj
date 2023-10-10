(ns bosquet.memory.weaviate
  (:import [io.weaviate.client Config WeaviateClient]
           [io.weaviate.client.base Result]
           [io.weaviate.client.v1.misc.model Meta]))

(defn client []
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

(comment
  (def c (client))
  (status c)
  #__)
