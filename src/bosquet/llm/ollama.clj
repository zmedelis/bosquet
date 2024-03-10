(ns bosquet.llm.ollama
  (:require
   [bosquet.env :as env]
   [bosquet.llm.http :as http]
   [bosquet.llm.oai-shaped-llm :as oai]
   [bosquet.utils :as u]))


(defn ->completion
  [{:keys [response message] :as x}
   #_{[{:keys [text message]} & _choices]    :choices
      {total_tokens      :total_tokens
       prompt_tokens     :prompt_tokens
       completion_tokens :completion_tokens} :usage}]
  (u/pp x)
  (if response response (oai/chatml->bosquet message))
  #_(assoc
     (cond
       message {wkk/generation-type :chat
                wkk/content         (chatml->bosquet message)}
       text    {wkk/generation-type :completion
                wkk/content         text})
     wkk/usage   {:prompt     prompt_tokens
                  :completion completion_tokens
                  :total      total_tokens}))


(defn chat-fn [{:keys [api-endpoint]}]
  (partial http/post (str api-endpoint "/chat") nil))


(defn completion-fn [{:keys [api-endpoint]}]
  (partial http/post (str api-endpoint "/generate") nil))


#_(defn create-completion
  "Make a call to Ollama

  - `service-cfg` will contain props needed to make call: endpoint, model defaults, etc
  - `params` is the main payload of the call containing model params, and prompt in `messages`
  - `content` is intended for `complete` workflow where we do not have chat `messages` in `params`"
  [{default-params :model-params :as service-cfg} params]
  (let [lm-call (completion-fn service-cfg)]
    (-> params
        #_(prep-params default-params)
        lm-call
        ->completion)))

(defn chat
  [service-cfg params]
  (let [lm-call (chat-fn service-cfg)]
    (-> params
        #_(prep-params default-params)
        lm-call
        ->completion)))


(defn complete
  [service-cfg params]
  (let [lm-call (completion-fn service-cfg)]
    (-> params
        #_(prep-params default-params)
        lm-call
        ->completion)))

(comment
  (complete (:ollama env/config)
            {:prompt "3/2="
             :stream false
             :model "llama2"})

  (chat (:ollama env/config)
        {:messages [{:role "user" :content "3/2="}]
         :stream false
         :model  "llama2"})
  #__)
