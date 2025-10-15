(ns bosquet.llm.ollama
  (:require
   [bosquet.env :as env]
   [bosquet.llm.http :as http]
   [bosquet.llm.oai-shaped-llm :as oai]
   [bosquet.llm.wkk :as wkk]
   [bosquet.llm.tools :as tools]))

(defn ->completion
  [{:keys [response message prompt_eval_count eval_count]
    ;; ollama returns 0 for prompt eval if the prompt was cached
    :or   {prompt_eval_count 0 eval_count 0}}]
  (assoc
   (cond
     message  {wkk/generation-type :chat
               wkk/content         (oai/chatml->bosquet message)}
     response {wkk/generation-type :completion
               wkk/content         response})
   wkk/usage {:prompt     prompt_eval_count
              :completion eval_count
              :total      (+ eval_count prompt_eval_count)}))

(defn- chat-fn [{:keys [api-endpoint]}]
  (partial http/resilient-post (str api-endpoint "/chat")))

(defn- completion-fn [{:keys [api-endpoint]}]
  (partial http/resilient-post (str api-endpoint "/generate")))

(defn- embedding-fn [{:keys [api-endpoint]}]
  (partial http/resilient-post (str api-endpoint "/embeddings")))

(defn- generate
  [default-params params gen-fn]
  (let [tools  (map tools/tool->function (wkk/tools params))
        tool-defs (wkk/tools params)
        params (-> params (assoc :stream false) (oai/prep-params default-params) (assoc :tools tools))]
    (-> (gen-fn params)
        (tools/apply-tools wkk/ollama params tool-defs gen-fn)
        ->completion)))

(defn chat
  [service-cfg params]
  (generate service-cfg params (chat-fn service-cfg)))

(defn complete
  [service-cfg params]
  (generate service-cfg (dissoc params wkk/tools) (completion-fn service-cfg)))

(defn create-embedding
  "Works as the equivalent of this:

  ```
  curl http://localhost:11434/api/embeddings -d '{
  \"model\": \"all-minilm\",
  \"prompt\": \"Here is an article about llamas...\"}'
  ```

  https://github.com/ollama/ollama/blob/main/docs/api.md#generate-embeddings"
  [service-cfg {:keys [model content]
                :or   {content identity}} payload]
  ((embedding-fn service-cfg)
   {:model  model
    :prompt (content payload)}))

(comment
  (create-embedding (env/config :ollama) {:model :llama3.2}
                    "Here is an article about llamas...")

  (create-embedding (env/config :ollama) {:model :all-minilm
                                          :content :text}
                    {:text "Here is an article about llamas..."
                     :score 100})
  (complete {:api-endpoint "http://localhost:11434/api"}
            {:model "llama3.2:3b"
             :prompt "why is the sky blue?"
             wkk/tools [#'tools/get-current-weather]})

  (chat {:api-endpoint "http://localhost:11434/api"}
        {:model "llama3.2:3b"
         :messages [{:role :user :content "What is the weather in san francisco?"}]
         wkk/tools [#'tools/get-current-weather]})

  (chat {:api-endpoint "http://localhost:11434/api"}
        {:model "llama3.2:3b"
         :messages [{:role :user :content "What is 2 plus 2 minus 2"}]
         wkk/tools [#'tools/add #'tools/sub]})
  (complete {:api-endpoint "http://localhost:11434/api"}
            {:model "llama3.2:3b"
             :prompt "The current weather in san francisco is"
             wkk/tools [#'tools/get-current-weather]}))
