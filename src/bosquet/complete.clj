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
        (get-in opts [system/llm-service-key gen-key])
        llm (system/llm-service service)]
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

(defn chat-completion [{system       llm.chat/system
                        conversation llm.chat/conversation
                        :as          context
                        :or          {conversation []}}
                       role message opts]
  (let [{:bosquet.llm/keys [service model-parameters]}
        (get-in opts [llm.chat/conversation])
        llm          (system/llm-service service)
        completion   (.chat llm
                            [{:role    :system
                              :content system}
                             {:role    role
                              :content message}]
                            model-parameters)]
    (assoc context
           llm.chat/conversation
           (conj conversation completion)
           llm.chat/last-message completion)))
