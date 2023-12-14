(ns bosquet.llm.http
  (:require
   [bosquet.utils :as u]
   [clojure.string :as string]
   [hato.client :as hc]))


(def client (hc/build-http-client {:connect-timeout 10000}))

(defn- json-params
  "Snake case keys from `:max-tokens` to `:max_tokens`"
  [params]
  (->> params
    (reduce-kv
      (fn [m k v]
        (assoc m
               (-> k name (string/replace "-" "_") keyword)
               v))
      {})
    u/write-json))

(defn post
  [url {api-key :api-key :as params}]
  (-> url
      (hc/post {:content-type :json
                :oauth-token  api-key
                :body         (json-params params)
                :http-client  client})
      :body
      (u/read-json)))
