(ns bosquet.llm.http
  (:require
   [bosquet.utils :as u]
   [clojure.string :as string]
   [hato.client :as hc]
   [taoensso.timbre :as timbre]))

(defn use-local-proxy
  "Use local proxy to log LLM API requests"
  ([] (use-local-proxy "localhost" 8080 "changeit"))
  ([host port password]
   (System/setProperty "javax.net.ssl.trustStore" (str (System/getProperty "user.home") "/.bosquet/keystore"))
   (System/setProperty "javax.net.ssl.trustStorePassword" password)
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

(defn- log-call
  [url params]
  (timbre/infof "💬 Calling %s with:" url)
  (doseq [[k v] (dissoc params :messages)]
    (timbre/infof "   %-15s%s" k v)))

(defn post
  [url api-key params]
  (log-call url params)
  (try
    (-> url
        (hc/post (merge {:content-type :json
                         :body         (-> params json-params u/snake_case)
                         :http-client  (client)}
                        (when api-key {:oauth-token api-key})))
        :body
        (u/read-json))
    (catch Exception e
      (let [{:keys [body status]} (ex-data e)]
        (timbre/errorf "LLM Call failed with HTTP status '%s' and error message '%s'" status (-> body u/read-json :message))
        (timbre/error "Call parameters:" (prn-str params))))))
