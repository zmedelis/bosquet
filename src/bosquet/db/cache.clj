(ns bosquet.db.cache
  (:require
   [bosquet.llm.wkk :as wkk]
   [clojure.core.cache.wrapped :as w]))

(defn ->cache []
  (w/fifo-cache-factory {}))

(def cache (->cache))

(defn cache-props
  [properties]
  (select-keys properties [wkk/model-params :messages :prompt]))

(defn evict
  [props]
  (w/evict cache (cache-props props)))

(defn lookup-or-call
  "Call `gen-fn` function with `properties` that are used as
  a key for cache. Same props (model params and context) will hit
  the cache"
  [gen-fn properties]
  (w/lookup-or-miss
   cache
   (cache-props properties)
   (fn [_item] (gen-fn properties))))
