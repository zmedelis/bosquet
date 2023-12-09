(ns bosquet.llm.lmstudio
  (:require
   [bosquet.llm.chat :as chat]
   [bosquet.llm.llm :as llm]
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


(defn- post-completion
  [params {:keys [api-endpoint]}]
  (let [res (hc/post (str api-endpoint "/chat/completions")
                     {:content-type :json
                      :body         (-> params fix-params u/write-json)})]
    (-> res
        :body
        (u/read-json))))


(defn- ->completion
  [{choices :choices {prompt_tokens     :prompt_tokens
                      completion_tokens :completion_tokens
                      total_tokens      :total_tokens} :usage}
   generation-type]
  (let [result (-> choices first :message chat/chatml->bosquet)]
    {llm/generation-type generation-type
     llm/content         (if (= :chat generation-type)
                           result
                           {:completion (:content result)})
     llm/usage           {:prompt     prompt_tokens
                          :completion completion_tokens
                          :total      total_tokens}}))


(def ^:private gen-type :gen-type)


(defn- chat-completion
  [messages {generation-type gen-type :as params} opts]
  (timbre/infof "💬 Calling LM Studio with:")
  (timbre/infof "\tParams: '%s'" (dissoc params :prompt))
  (timbre/infof "\tConfig: '%s'" opts)
  (let [messages (if (string? messages)
                   [(chat/speak chat/user messages)]
                   messages)]
    (-> params
        (assoc :messages messages)
        (post-completion opts)
        (->completion generation-type))))


(deftype LMStudio
    [opts]
    llm/LLM
    (service-name [_this] ::lm-studio)
    (generate [_this prompt params]
      (chat-completion prompt (assoc params gen-type :complete) opts))
    (chat     [_this conversation params]
      (chat-completion conversation (assoc params gen-type :chat) opts)))

(comment
  (def llm (LMStudio.
            {:api-endpoint "http://localhost:1234/v1"}))
  (.chat llm
         [(chat/speak chat/system "You are a brilliant cook.")
          (chat/speak chat/user "What is a good cookie?")]
         {})
  #__)
