(ns bosquet.llm.http
  (:require [clj-http.client :as client]
            [bosquet.utils :as u]
            [taoensso.timbre :as timbre]
            [net.modulolotus.truegrit.circuit-breaker :as cb]))

(defn use-local-proxy
  "Use local proxy to log LLM API requests"
  ([] (use-local-proxy "localhost" 8080 "changeit"))
  ([host port password]
   (System/setProperty "javax.net.ssl.trustStore" (str (System/getProperty "user.home") "/.bosquet/keystore"))
   (System/setProperty "javax.net.ssl.trustStorePassword" password)
   (System/setProperty "https.proxyHost" host)
   (System/setProperty "https.proxyPort" (str port))))


(defn post
  ([url params] (post url nil params))
  ([url http-opts params]
   (u/log-call url params)
   (try
     (let [request  (merge {:content-type :json
                            :accept       :json
                            :body         (->> params u/snake-case u/write-json)}
                           http-opts)
           response (client/post url request)]
       (-> response :body (u/read-json)))
     (catch Exception e
       (.printStackTrace e)
       (let [{:keys [body status]}   (ex-data e)
             {:keys [message error]} (u/read-json body)]
         (timbre/error "Call failed")
         (timbre/errorf "- HTTP status '%s'" status)
         (timbre/errorf "- Error message '%s'" (or message error)))))))


(def resilient-post*
  (cb/wrap (fn [& args]
             (apply post args))
           u/rest-service-cb))

(defn resilient-post [& args]
  (apply resilient-post* args))

