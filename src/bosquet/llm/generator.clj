(ns bosquet.llm.generator
  (:require
   [bosquet.converter :as converter]
   [bosquet.db.cache :as cache]
   [bosquet.env :as env]
   [bosquet.llm :as llm]
   [bosquet.llm.wkk :as wkk]
   [bosquet.template.read :as template]
   [bosquet.utils :as u]
   [com.wsscode.pathom3.connect.indexes :as pci]
   [com.wsscode.pathom3.connect.operation :as pco]
   [com.wsscode.pathom3.interface.smart-map :as psm]
   [com.wsscode.pathom3.plugin :as p.plugin]
   [selmer.parser :as selmer]
   [taoensso.timbre :as timbre]))

(defn- resolver-error-wrapper
  [env]
  (p.plugin/register
   env
   {::p.plugin/id 'err
    :com.wsscode.pathom3.connect.runner/wrap-resolver-error
    (fn [_]
      (fn [_env {op-name :com.wsscode.pathom3.connect.operation/op-name} error]
        (timbre/errorf "Resolver operation '%s' failed" op-name)
        (timbre/error error)))}))

(defn ->chatml [messages]
  (map
   (fn [[role content]] {:role role :content content})
   messages))

(defn- call-llm
  "Make a call to the LLM service.
  - `llm-config` provides a map containing LLM service configurations, the
     LLM to call is specified in
  - `properties` providing model parameters and other details of LLM invocation
  - `messages` contains the context/prompt to be supplied to LLM."
  [llm-config
   {llm-impl     wkk/service
    model-params wkk/model-params
    use-cache    wkk/cache
    :as          properties}
   messages]
  (if (map? properties)
    (try
      (let [format-fn      (partial converter/coerce (wkk/output-format properties))
            service-config (dissoc (llm-impl llm-config) wkk/gen-fn wkk/chat-fn)
            chat-fn        (partial (get-in llm-config [llm-impl wkk/chat-fn]) service-config)
            params         (assoc model-params :messages (->chatml messages))
            result         (if use-cache
                             (cache/lookup-or-call chat-fn params)
                             (chat-fn params))]
        (update-in result [wkk/content :content] format-fn))
      (catch Exception e
        (timbre/error e)))
    (timbre/warnf ":assistant instruction does not contain AI gen function spec")))

(def conversation
  "Result map key holding full chat conversation including generated parts"
  :bosquet/conversation)

(def completions
  "Result map key holding LLM generated parts"
  :bosquet/completions)

(def usage
  "Result map key holding LLM token usage data"
  :bosquet/usage)

(defn total-usage
  [usages]
  (reduce-kv
   (fn [{:keys [prompt completion total]
         :or   {prompt 0 completion 0 total 0}
         :as   aggr}
        _k
        {p :prompt c :completion t :total
         :or {p 0 c 0 t 0}}]
     (assoc aggr
            :prompt (+ p prompt)
            :completion (+ c completion)
            :total (+ t total)))
   {}
   usages))

(defn chat
  "Chat completion using
  - `llm-config` holding LLM service configuration
  - `messages` chat message tuples
               [[:system \"You are ...\"]
                [:user \"Please, ...\"]
                [:assistant {...}]]
  - `inputs` data map to fill the tempalte slots"
  [llm-config messages inputs]
  (loop [[[role content] & messages] messages
         processed-messages          []
         accumulated-usage           {}
         ctx                         inputs]
    (if (nil? role)
      {conversation processed-messages
       completions  (apply dissoc ctx (keys inputs))
       usage        (assoc accumulated-usage
                           :bosquet/total (total-usage accumulated-usage))}
      (if (= :assistant role)
        (let [{{gen-result :content} wkk/content
               usage                 wkk/usage} (call-llm llm-config content processed-messages)
              var-name                          (wkk/var-name content)]
          (recur messages
                 (conj processed-messages [role gen-result])
                 (assoc accumulated-usage var-name usage)
                 (assoc ctx var-name gen-result)))
        (let [tpl-result (first (template/render (u/join-coll content) ctx))]
          (recur messages
                 (conj processed-messages [role tpl-result])
                 accumulated-usage
                 ctx))))))

(defn- ->resolver
  [name-sufix message-key input resolve-fn]
  (pco/resolver
   {::pco/op-name (-> message-key .-sym (str "-" name-sufix) symbol)
    ::pco/output  [message-key]
    ::pco/input   input
    ::pco/resolve resolve-fn}))

(defn- generation-resolver
  [llm-config message-key {ctx-var wkk/context :as message-content}]
  (if (map? message-content)
    (if ctx-var
      (->resolver "ai-gen" message-key [(wkk/context message-content)]
       (fn [{entry-tree :com.wsscode.pathom3.entity-tree/entity-tree*} _input]
         (try
           (let [full-text (get @entry-tree ctx-var)
                 gen-res   (call-llm llm-config message-content [[:user full-text]])
                 result    (get-in gen-res [wkk/content :content])]
             {message-key result})
           (catch Exception e
             (timbre/error e)))))
      (timbre/warnf "Context var is not set in generation spec. Add 'llm/context' to '%s'" message-key))
    ;; TEMPLATE
    (let [message-content (u/join-coll message-content)]
      (->resolver "template" message-key (vec (selmer/known-variables message-content))
       (fn [{entry-tree :com.wsscode.pathom3.entity-tree/entity-tree*} _input]
            {message-key (first (template/render message-content @entry-tree))})))))

(defn- prompt-indexes [llm-config messages]
  (pci/register
   (mapv
    (fn [prompt-key]
      (generation-resolver llm-config
                           prompt-key
                           (get messages prompt-key)))
    (keys messages))))

(defn complete
  [llm-config messages vars-map]
  (let [indexes  (prompt-indexes llm-config messages)
        sm       (psm/smart-map indexes vars-map)
        resolver (resolver-error-wrapper sm)]
    (select-keys resolver (keys messages))))

(def default-template-prompt
  "Simple string template generation case does not create var names for the completions,
  compared to Map generation where map keys are the var names.

  This is a key for prompt entry"
  ::prompt)
(def default-template-completion
  "Simple string template generation case does not create var names for the completions,
  compared to Map generation where map keys are the var names.

  This is a key for completion entry"
  ::completion)


(defn append-generation-instruction
  "If template does not specify generation function append the default one."
  [string-template]
  {default-template-prompt     string-template
   default-template-completion {wkk/service (env/default-llm)
                                wkk/context default-template-prompt}})

(defn generate
  "
  Generate completions for various modes. Generation mode is determined
  by the type of the `messages`:

  - Vector of tuples, triggers `chat` mode completion
    ```
     [[:system \"You are ...\"]
      [:user \"Please, ...\"]
      [:assistant {...}]]
    ```
  - A map, triggers `graph` mode completion
    ```
    {:question-answer \"Question: {{question}}\"
     :answer          {...}}
    ```
  - A `string` results in a `template` completion mode

  `llm-config` holds configuration to make LLM calls and `inputs` has a data map
  for template slot filling."
  ([messages] (generate llm/default-services messages {}))
  ([messages inputs]
   (generate llm/default-services messages inputs))
  ([llm-config messages vars-map]
   (cond
     (vector? messages) (chat llm-config messages vars-map)
     (map? messages)    (complete llm-config messages vars-map)
     (string? messages) (default-template-completion
                         (complete llm-config (append-generation-instruction messages) vars-map)))))

(defn llm
  "A helper function to create LLM spec for calls during the generation process.
  It comes back with a map constructed from `service` and `args`:

  ```
  {:llm/service      service
   :llm/cache        true
   :llm/model-params params}
  ```"
  [service & args]
  (assoc (apply hash-map args) wkk/service service))

(comment

  (generate
   "When I was 6 my sister was half my age. Now I’m 70 how old is my sister?")

  (generate
   "When I was {{age}} my sister was half my age. Now I’m 70 how old is my sister?"
   {:age 13})

  (generate
   [[:system "You are an amazing writer."]
    [:user ["Write a synopsis for the play:"
            "Title: {{title}}"
            "Genre: {{genre}}"
            "Synopsis:"]]
    [:assistant (llm wkk/openai
                     wkk/model-params {:temperature 0.8 :max-tokens 120}
                     wkk/var-name :synopsis)]
    [:user "Now write a critique of the above synopsis:"]
    [:assistant (llm wkk/openai
                     wkk/model-params {:temperature 0.2 :max-tokens 120}
                     wkk/var-name     :critique)]]
   {:title "Mr. X"
    :genre "Sci-Fi"})

  (generate
   llm/default-services
   {:question-answer "Question: {{question}}  Answer:"
    :answer          (llm wkk/openai
                          wkk/context :question-answer
                          wkk/cache true)
    :self-eval       ["Question: {{question}}"
                      "Answer: {{answer}}"
                      ""
                      "Is this a correct answer?"]
    :test            (llm wkk/openai wkk/context :self-eval)}
   {:question "What is the distance from Moon to Io?"})

  (generate
   {:astronomy (u/join-nl
                "As a brilliant astronomer, list distances between planets and the Sun"
                "in the Solar System. Provide the answer in JSON map where the key is the"
                "planet name and the value is the string distance in millions of kilometers."
                "Generate only JSON omit any other prose and explanations.")
    :answer    (llm wkk/openai
                    wkk/output-format :json
                    wkk/context :astronomy)})

  #_(generate
     [(chat/speak chat/user "What's the weather like in San Francisco, Tokyo, and Paris?")]
     {}
     {chat/conversation
      {wkk/service [:llm/openai :provider/openai]
       wkk/model-parameters
       {:temperature 0
        :tools       [{:type "function"
                       :function
                       {:name       "get-current-weather"
                        :decription "Get the current weather in a given location"
                        :parameters {:type       "object"
                                     :required   [:location]
                                     :properties {:location {:type        "string"
                                                             :description "The city and state, e.g. San Francisco, CA"}
                                                  :unit     {:type "string"
                                                             :enum ["celsius" "fahrenheit"]}}}}}]

        :model "gpt-3.5-turbo"}}})

  #__)
