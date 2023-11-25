(ns bosquet.llm.lmstudio
  (:require
   [bosquet.llm.chat :as chat]
   [bosquet.llm.llm :as llm]
   [taoensso.timbre :as timbre]
   [hato.client :as hc]
   [jsonista.core :as j]))

(defn- chat-completion
  [messages params {:keys [api-endpoint]}]
  (let [res (hc/post api-endpoint
                    {:content-type :json
                     :body         (j/write-value-as-string
                                    {:messages          messages
                                     :max_tokens        150
                                     :temperature       0.9
                                     :top_p             1
                                     :frequency_penalty 0.0
                                     :presence_penalty  0.6
                                     :stop              ["\n"]})})]
    (tap> (:body res))
    (-> res
        :body
        (j/read-value j/keyword-keys-object-mapper)
        :choices
        first
        :message)))

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
