(ns bosquet.complete
  (:require [bosquet.system :as system]
            [clojure.core :as core]
            [clojure.core.cache.wrapped :as cache]))

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

(defn complete [prompt {gen-key :the-key :as opts}]
  (let [llm (system/llm-service (get-in opts [system/llm-service-key gen-key]))]
    (.generate llm prompt opts))
  ;; TODO bring back the cache immediately
  ;; use Integrant system to setup the cache component
  #_(let [complete-fn
          (cond
            (= :azure impl)  openai/complete-azure-openai
            (= :openai impl) openai/complete-openai
            (fn? impl)       impl)]

      (if-let [cache (:cache opts)]
        (complete-with-cache prompt opts (ensure-atom cache) complete-fn)
        (complete-fn prompt opts))))
