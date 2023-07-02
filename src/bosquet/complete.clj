(ns bosquet.complete
  (:require [bosquet.openai :as openai]
            [clojure.core.cache.wrapped :as cache]
            [clojure.core :as core]))

(defn complete-with-cache [prompt params cache complete-fn]
  (cache/lookup-or-miss
   cache
   {:prompt prompt
    :params (dissoc params :cache)}
   (fn [item]
     (complete-fn
      (:prompt item)
      (:params item)))))

(defn atom? [a] (= (type a) clojure.lang.Atom))

;; lookup-or-miss works with an atom of a cache
(defn ensure-atom [x]
  (if (atom? x) x (atom x)))

(defn complete [prompt {:keys [impl]
                        :or   {impl :openai}
                        :as   opts}]
  (let [complete-fn
        (cond
          (= :azure impl)  openai/complete-azure-openai
          (= :openai impl) openai/complete-openai
          (fn? impl)       impl)]

    (if-let [cache (:cache opts)]
      (complete-with-cache prompt opts (ensure-atom cache) complete-fn)
      (complete-fn prompt opts))))
