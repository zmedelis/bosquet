(ns bosquet.llm.http
  (:require
   [bosquet.utils :as u]
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


(defn post
  ([url params] (post url nil params))
  ([url headers params]
   (u/log-call url params)
   (try
     (-> url
         (hc/post (merge {:content-type :json
                          :body         (->> params u/snake-case u/write-json)
                          :http-client  (client)}
                         headers))
         :body
         (u/read-json))
     (catch Exception e
       (.printStackTrace e)
       (let [{:keys [body status]}   (ex-data e)
             {:keys [message error]} (u/read-json body)]
         (timbre/error "Call failed")
         (timbre/errorf "- HTTP status '%s'" status)
         (timbre/errorf "- Error message '%s'" (or message error)))))))
