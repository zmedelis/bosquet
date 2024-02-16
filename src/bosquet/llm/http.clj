(ns bosquet.llm.http
  (:require
   [bosquet.utils :as u]
   [clojure.string :as string]
   [hato.client :as hc]))

(defn client
  []
  (hc/build-http-client {:connect-timeout 10000}))

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
  [url api-key params]
  (-> url
      (hc/post (merge {:content-type :json
                       :body         (-> params json-params u/snake_case)
                       :http-client  (client)}
                      (when api-key {:oauth-token api-key})))
      :body
      (u/read-json)))
