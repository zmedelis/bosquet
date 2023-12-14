(ns bosquet.llm.http
  (:require
   [bosquet.utils :as u]
   [clojure.string :as string]
   [hato.client :as hc]
   [taoensso.timbre :as timbre]))

(defn- fix-params
  "Snake case keys from `:max-tokens` to `:max_tokens`"
  [params]
  (reduce-kv
   (fn [m k v]
     (assoc m
            (-> k name (string/replace "-" "_") keyword)
            v))
   {}
   params))

(defn post
  [{:keys [api-endpoint] :as params}]
  (timbre/spy
   (-> api-endpoint
       (hc/post {:content-type :json
                 :body         (-> params
                                   (dissoc :api-endpoint)
                                   fix-params u/write-json)})
       :body
       (u/read-json))))
