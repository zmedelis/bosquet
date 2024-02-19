(ns bosquet.llm.http
  (:require
   [bosquet.utils :as u]
   [clojure.string :as string]
   [hato.client :as hc]))

(defn use-local-proxy
  "Use local proxy to log LLM API requests"
  ([] (use-local-proxy "localhost" 8080))
  ([host port]
   (System/setProperty "javax.net.ssl.trustStore" (str (System/getProperty "user.home") "/.bosquet/keystore"))
   (System/setProperty "javax.net.ssl.trustStorePassword" "changeit")
   (System/setProperty "https.proxyHost" host)
   (System/setProperty "https.proxyPort" (str port))))

(defn client
  ([] (client nil))
  ([{:keys [connect-timeout]
     :or   {connect-timeout 10000}
     :as   opts}]
   (hc/build-http-client
    (merge opts
           {:connect-timeout connect-timeout}))))

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
