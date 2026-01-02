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

(defn- classify-error [status]
  (cond
    (= status 429)      :rate-limit
    (= status 503)      :service-unavailable
    (= status 502)      :bad-gateway
    (= status 504)      :gateway-timeout
    (#{500} status)     :server-error
    (#{400 422} status) :bad-request
    (#{401 403} status) :auth-error
    (#{404} status)     :not-found
    :else               :unknown))

(def retryable-errors #{:rate-limit :service-unavailable :bad-gateway :gateway-timeout})
(def recoverable-errors #{:server-error :rate-limit :service-unavailable})

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
       (-> response :body u/read-json))
     (catch Exception e
       (let [{:keys [body status]} (ex-data e)
             {:keys [message error]} (when body (u/read-json body))
             error-type (classify-error status)]
         (timbre/errorf "HTTP %s: %s" status (or message error))
         (throw (ex-info (str "LLM API error: " (or message error "Unknown error"))
                         {:type         error-type
                          :status       status
                          :message      (or message error)
                          :retryable?   (retryable-errors error-type)
                          :recoverable? (recoverable-errors error-type)}
                         e)))))))

(def resilient-post*
  (cb/wrap (fn [& args]
             (apply post args))
           u/rest-service-cb))

(defn resilient-post [& args]
  (apply resilient-post* args))
