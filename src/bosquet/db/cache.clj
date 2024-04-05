(ns bosquet.db.cache
  (:require
   [bosquet.llm.wkk :as wkk]
   [clojure.core.cache.wrapped :as w]))


(defn ->cache []
  (w/fifo-cache-factory {}))


(def cache (->cache))


(defn evict
  [props]
  (w/evict cache props))


(defn evict-all
  []
  (doseq [k (keys @cache)]
    (evict k)))


(defn lookup-or-call
  "Call `gen-fn` function with `properties` that are used as
  a key for cache. Same props (model params and context) will hit
  the cache"
  [gen-fn properties]
  (w/lookup-or-miss cache properties
   (fn [_item] (gen-fn properties))))
