(ns bosquet.llm.resilience
  (:require
   [bosquet.llm.wkk :as wkk]
   [net.modulolotus.truegrit.circuit-breaker :as cb]
   [taoensso.timbre :as timbre]))

(defonce ^:private circuit-breakers (atom {}))

(defn- get-or-create-cb 
  [provider-key 
   config]
  (or (get @circuit-breakers provider-key)
      (let [new-cb (cb/circuit-breaker
                    (name provider-key)
                    {:failure-rate-threshold                       (:failure-threshold config 50)
                     :minimum-number-of-calls                      (:min-calls config 5)
                     :wait-duration-in-open-state                  (:timeout-ms config 60000)
                     :permitted-number-of-calls-in-half-open-state (:success-threshold config 2)})]
        (swap! circuit-breakers assoc provider-key new-cb)
        new-cb)))

(defn- sleep-backoff 
  [attempt 
   base-ms]
  (Thread/sleep (long (min (* base-ms (Math/pow 2 attempt)) 30000))))

(defn with-retry
  [f {:keys [max-attempts backoff-ms] :or {max-attempts 3 backoff-ms 1000}}]
  (loop [attempt 0]
    (let [result (try
                   {:success (f)}
                   (catch Exception e
                     {:error e :retryable? (:retryable? (ex-data e))}))]
      (cond
        (:success result)
        (:success result)

        (and (:retryable? result) (< attempt (dec max-attempts)))
        (do
          (timbre/infof "Retry %d/%d" (inc attempt) max-attempts)
          (sleep-backoff attempt backoff-ms)
          (recur (inc attempt)))

        :else
        (throw (:error result))))))

(defn- try-provider 
  [call-fn 
   provider 
   llm-config 
   messages 
   cb-config 
   retry-config]
  (let [provider-key (wkk/service provider)
        wrapped (cb/wrap (fn []
                           (with-retry #(call-fn llm-config 
                                                 provider 
                                                 messages)  
                                       retry-config))
                         (get-or-create-cb provider-key cb-config))]
    (wrapped)))

(defn with-fallback
  [call-fn {:keys [primary fallbacks retry circuit-breaker]} llm-config messages]
  (loop [[provider & remaining] (cons primary fallbacks)
         last-error nil]
    (if provider
      (let [result (try
                     {:success (try-provider call-fn provider llm-config messages circuit-breaker retry)}
                     (catch Exception e
                       (timbre/warnf "Provider %s failed: %s" (wkk/service provider) (.getMessage e))
                       {:error e :recoverable? (:recoverable? (ex-data e))}))]
        (cond
          (:success result) (:success result)
          (:recoverable? result) (recur remaining (:error result))
          :else (throw (:error result))))
      (throw (ex-info "All providers failed" {:type :all-providers-failed} last-error)))))
