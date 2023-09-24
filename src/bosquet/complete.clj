(ns bosquet.complete
  (:require
   [bosquet.llm.chat :as llm.chat]
   [bosquet.system :as system]
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
  (let [{:bosquet.llm/keys [service model-parameters]}
        (get-in opts [system/llm-config gen-key])
        llm (system/get-service service)]
    (.generate llm prompt model-parameters))
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

(defn chat-completion [messages
                       opts]
  (let [{:bosquet.llm/keys    [service model-parameters]
         :bosquet.memory/keys [type]}
        (get-in opts [llm.chat/conversation])
        llm          (system/get-service service)
        memory       (system/get-memory type)
        memories     (.recall memory {:limit 10})
        completion   (.chat llm (concat memories messages) model-parameters)]
    (.remember memory messages)
    (.remember memory completion)
    completion))
