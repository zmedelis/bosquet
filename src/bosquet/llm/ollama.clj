(ns bosquet.llm.ollama
  (:require
   [bosquet.env :as env]
   [bosquet.llm.http :as http]
   [bosquet.llm.oai-shaped-llm :as oai]
   [bosquet.llm.wkk :as wkk]))


(defn ->completion
  [{:keys [response message prompt_eval_count eval_count]
                                        ; ollama returns 0 for prompt eval if the prompt was cached
    :or   {prompt_eval_count 0}}]
  (assoc
   (cond
     message  {wkk/generation-type :chat
               wkk/content         (oai/chatml->bosquet message)}
     response {wkk/generation-type :completion
               wkk/content         response})
   wkk/usage {:prompt     prompt_eval_count
              :completion eval_count
              :total      (+ eval_count prompt_eval_count)}))


(defn chat-fn [{:keys [api-endpoint]}]
  (partial http/post (str api-endpoint "/chat") nil))


(defn completion-fn [{:keys [api-endpoint]}]
  (partial http/post (str api-endpoint "/generate") nil))


(defn- generate
  [default-params params gen-fn]
  (-> params
      ;; no support for streaming for now
      (assoc :stream false)
      (oai/prep-params default-params)
      gen-fn
      ->completion))


(defn chat
  [service-cfg params]
  (generate service-cfg params (chat-fn service-cfg)))


(defn complete
  [service-cfg params]
  (generate service-cfg params (completion-fn service-cfg)))
