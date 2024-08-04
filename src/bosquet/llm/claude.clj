(ns bosquet.llm.claude
  (:require
   [bosquet.env :as env]
   [bosquet.llm.http :as http]
   [bosquet.llm.oai-shaped-llm :as oai]
   [bosquet.llm.wkk :as wkk]
   [bosquet.llm.schema :as schema]
   [bosquet.utils :as u]))




(defn ->completion
  [{:keys [response message prompt_eval_count eval_count]
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


(defn usage->canonical
  [{:keys [input_tokens output_tokens]}]
  {schema/usage-in-count input_tokens
   schema/usage-out-count output_tokens
   schema/usage-total-count (+ output_tokens input_tokens)})


(defn- header [key]
  {:headers {"x-api-key"         key
             "anthropic-version" "2023-06-01"}})


(defn messages
  ([params] (messages (wkk/openai env/config) params))
  ([{key :api-key url :api-endpoint} params]
   (u/log-call url params)
   (let [{:keys [content usage]} (http/post (str url "/messages") (header key) params)]
     {wkk/generation-type :chat
      wkk/content         {:role    :assistant
                           :content (-> content last :text)}
      wkk/usage           (usage->canonical usage)})))
