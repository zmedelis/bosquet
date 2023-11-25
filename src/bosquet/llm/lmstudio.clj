(ns bosquet.llm.lmstudio
  (:require
   [bosquet.llm.chat :as chat]
   [bosquet.llm.llm :as llm]
   [clojure.string :as string]
   [hato.client :as hc]
   [jsonista.core :as j]
   [taoensso.timbre :as timbre]))

(defn- fix-params
  [params]
  (reduce-kv
   (fn [m k v]
     (assoc m
            (-> k name (string/replace "-" "_") keyword)
            v))
   {}
   params))

(defn- post-completion
  [params {:keys [api-endpoint]}]
  (prn ">>>> "(-> params fix-params j/write-value-as-string))
  (let [res (hc/post (str api-endpoint "/chat/completions")
                     {:content-type :json
                      :body         (-> params fix-params j/write-value-as-string)})]
    (clojure.pprint/pprint res)
    (-> res
        :body
        (j/read-value j/keyword-keys-object-mapper))))

(defn- ->completion [result]
  (-> result :choices first :message))

(defn- ->error [e]
  (let [{:keys [message code]} e]
    (throw (ex-info message {:code code}))))

(defn- chat-completion
  [messages params opts]
  (timbre/infof "💬 Calling LM Studio with:")
  (timbre/infof "\tParams: '%s'" (dissoc params :prompt))
  (timbre/infof "\tConfig: '%s'" opts)
  (try
    (-> params
        (assoc :messages messages)
        (post-completion opts)
        ->completion)
    (catch Exception e
      (throw e))))

(deftype LMStudio
    [opts]
    llm/LLM
    (service-name [_this] ::lm-studio)
    (generate [this prompt params]
      (timbre/warn "LMStudio does not support 'completions'. Forcing to 'chat'.")
      (.chat this prompt params))
    (chat     [_this conversation params]
      (chat-completion conversation params opts)))

(comment
  (def llm (LMStudio.
            {:api-endpoint "http://localhost:1234/v1/chat/completions"}))
  (.chat llm
         [(chat/speak chat/system "You are a brilliant cook.")
          (chat/speak chat/user "What is a good cookie?")]
         {})
  #__)
