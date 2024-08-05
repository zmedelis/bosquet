(ns bosquet.llm.generator
  (:require
   [bosquet.converter :as converter]
   [bosquet.db.cache :as cache]
   [bosquet.env :as env]
   [bosquet.llm.gen-data :as gd]
   [bosquet.llm.wkk :as wkk]
   [bosquet.template.selmer :as selmer]
   [bosquet.utils :as u]
   [clojure.set :as set]
   [clojure.string :as string]
   [com.wsscode.pathom3.connect.indexes :as pci]
   [com.wsscode.pathom3.connect.operation :as pco]
   [com.wsscode.pathom3.entity-tree :as pet]
   [com.wsscode.pathom3.interface.eql :as p.eql]
   [com.wsscode.pathom3.plugin :as p.plugin]
   [jsonista.core :as j]
   [taoensso.timbre :as timbre]))

(def default-template-prompt
  "Simple string template generation case does not create var names for the completions,
  compared to Map generation where map keys are the var names.

  This is a key for prompt entry"
  :bosquet.template/prompt)


(def default-template-completion
  "Simple string template generation case does not create var names for the completions,
  compared to Map generation where map keys are the var names.

  This is a key for completion entry"
  :bosquet.template/completion)


(defn llm
  "A helper function to create LLM spec for calls during the generation process.
  It comes back with a map constructed from `service-or-model` and `args`.
  `service-or-model` can be one of:
  - `serivice` (like openai, mistral, ...), in this case args need to specify
     {:llm/model-params {:model :x}}
  - `model` in this case `service` will be determined from `env/model-providers`,
    no need to specify {:llm/model-params {:model :x}}
  ```
  {:llm/service      service
   :llm/cache        true
   :llm/model-params params}
  ```"
  [service-or-model & args]
  (if-let [service (env/model-providers service-or-model)]
    (-> (apply hash-map args)
        (assoc wkk/service service)
        (assoc-in [wkk/model-params :model] service-or-model))
    (assoc (apply hash-map args) wkk/service service-or-model)))


(defn fun
  [impl args]
  {wkk/fun-impl impl
   wkk/fun-args args})


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

(defn ->chatml
  "Convert `messages` in tuple format ot ChatML. There is a caviat. When
  `content` might not be a string (a likely case when one LLM call result in say JSON
  feeds into another call."
  [messages]
  (map
   (fn [[role content]]
     {:role role
      :content (if (map? content)
                 (j/write-value-as-string content)
                 content)})
   messages))


(defn call-llm
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
  (if-let [chat-impl (get-in llm-config [llm-impl wkk/chat-fn])]
    (let [format-fn      (partial converter/coerce (wkk/output-format properties))
          service-config (dissoc (llm-impl llm-config)
                                 wkk/complete-fn wkk/chat-fn wkk/embed-fn)
          chat-fn        (partial (if (symbol? chat-impl)
                                    ;; symbol comes from edn configs
                                    (requiring-resolve chat-impl)
                                    ;; this is when llm config has fn ref
                                    chat-impl)
                                  service-config)
          params         (merge
                          (get-in llm-config [llm-impl :model-params])
                          (assoc model-params :messages (->chatml messages)))

          result         (if use-cache
                           (cache/lookup-or-call chat-fn params)
                           (chat-fn params))]
      (update-in result [wkk/content :content] format-fn))
    (timbre/warnf "Generation instruction does not contain AI gen function spec")))

(def conversation
  "Result map key holding full chat conversation including generated parts"
  :bosquet/conversation)

(def completions
  "Result map key holding LLM generated parts"
  :bosquet/completions)

(def usage
  "Result map key holding LLM token usage data"
  :bosquet/usage)

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
                           :bosquet/total (gd/total-usage accumulated-usage))}
      (if (= :assistant role)
        (let [{{gen-result :content} wkk/content
               usage                 wkk/usage} (call-llm llm-config content processed-messages)
              var-name                          (wkk/var-name content)]
          (recur messages
                 (conj processed-messages [role gen-result])
                 (assoc accumulated-usage var-name usage)
                 (assoc ctx var-name gen-result)))
        (let [tpl-result (selmer/render (u/join-coll content) ctx)]
          (recur messages
                 (conj processed-messages [role tpl-result])
                 accumulated-usage
                 ctx))))))


(defn- ->resolver
  [name-sufix message-key input resolve-fn]
  (timbre/infof "resolver: (%s%s) => %s" name-sufix (if (seq input) (str " " (string/join " " input)) "") message-key)
  (pco/resolver
   {::pco/op-name (-> message-key .-sym (str "-" name-sufix) symbol)
    ::pco/output  [message-key]
    ::pco/input   (vec input)
    ::pco/resolve resolve-fn}))


(defn find-refering-templates
  "Given all the templates in a `context-map` find out which ones
  have references to `var-name`"
  [var-name context-map]
  (reduce-kv
   (fn [refs tpl-name tpl]
     (if (and (string? tpl) (contains? (-> tpl selmer/known-variables-in-order set) var-name))
       (conj refs tpl-name)
       refs))
   []
   context-map))


(defn- entry
  [{entry-tree ::pet/entity-tree*} entry-key]
  (get @entry-tree entry-key))

(selmer/set-missing-value-formatter)


(defn run-node-function
  "Run a function definition in the prompt tree.
  - `node` is the function defining node in the tree
  - `available-data` already resolved data, must contain function params"
  [node available-data]
  (let [args (reduce (fn [m k] (conj m (get available-data k)))
                     []
                     (mapv keyword (wkk/fun-args node)))]
    (apply (wkk/fun-impl node) args)))


(defn- render
  [{entry-tree ::pet/entity-tree*} content]
  (selmer/render content
                 (merge
                  @entry-tree
                  (gd/reduce-gen-graph
                   (fn [m k v] (assoc m k (completions v)))
                   @entry-tree))))


(defn- llm-node?
  "Is this node defining an LLM call?"
  [node]
  (and (map? node) (contains? node wkk/service)))


(defn- fun-node?
  "Is this node defining a custom function call?"
  [node]
  (and (map? node) (contains? node wkk/fun-impl)))


(defn- template-node?
  "Is this node defining a string template?"
  [node]
  (not (map? node)))


(defn- generation-resolver
  [llm-config message-key context vars-map]
  (let [node (message-key context)
        refs (find-refering-templates message-key context)]
    (cond
      ;; Generation node
      (llm-node? node)
      (mapv
       (fn [refering-template-key]
         (->resolver (str "ai-gen-" (name refering-template-key)) message-key
                     [refering-template-key]
                     (fn [env _input]
                       (let [txt (render env (selmer/clear-gen-var-slot (entry env refering-template-key) message-key))
                             {gen-usage wkk/usage {gen-content :content} wkk/content}
                             (call-llm llm-config node [[:user txt]])]
                         {message-key {completions gen-content
                                       usage       gen-usage}}))))
       refs)

      ;; Function call node
      (fun-node? node)
      (mapv
       (fn [refering-template-key]
         (->resolver (str "fn-call-" (name refering-template-key)) message-key
                     [refering-template-key]
                     (fn [_env _input]
                       {message-key
                        (str
                         (run-node-function node
                                            (merge vars-map context)))})))
       refs)


      ;; Template node
      (template-node? node)
      (let [message-content (selmer/render node vars-map)]
        (->resolver "template" message-key
                    ;; or num/str check is to allow numbers or strings
                    ;; as template values, needs reviewing (remove map?)
                    ;; should work better to drom all non generating nodes
                    (vec (filter #(or (string? (get context %))
                                      (number? (get context %)))
                                 (selmer/known-variables-in-order message-content)))
                    (fn [{entry-tree ::pet/entity-tree*} _input]
                      (let [result (selmer/render message-content
                                                  (merge
                                                   @entry-tree
                                                   (gd/reduce-gen-graph
                                                    (fn [m k v] (assoc m k (completions v)))
                                                    @entry-tree)))]
                        {message-key result})))))))


(defn- prompt-indexes [llm-config context vars-map]
  (pci/register
   (mapv (fn [prompt-key]
           (generation-resolver llm-config prompt-key context vars-map))
         (keys context))))


(defn append-generation-instruction
  "If template does not specify generation function append the default one."
  [string-template]
  {default-template-prompt     (selmer/append-slot string-template default-template-completion)
   default-template-completion (env/default-service)})


(defn gen-environment
  [llm-config context vars-map]
  (-> (prompt-indexes llm-config context vars-map)
      (resolver-error-wrapper)))


(defn- prep-graph
  "Join strings if tempalte is provided as collection"
  [graph available-data]
  (->> graph
       (reduce-kv
        (fn [m k v]
          (assoc m k (if (vector? v) (u/join-coll v) v)))
        {})
       ;; FIXME this is not good. First pass to join prompt vectors
       ;; another pass to fill in tempalte data slots,
       ;; two reduce is crap, but then there are fundamental issues with deps
       ;; inside for loops and so on
       (reduce-kv
        (fn [m k v]
          (assoc m k (cond
                       (string? v)      (selmer/render v available-data)
                       #_ #_(wkk/fun-impl v) (run-node-function v available-data)
                       :else            v)))
        {})))


(defn- template->chat
  "Convert Selmer template into chat structure. This
  **really** needs refactoring!!!"
  [template generators]
  (let [gen-var-re #"(?sm)\{\{.+?\}\}"
        elements (remove
                  string/blank?
                  (interleave
                   (string/split template gen-var-re)
                   (conj
                    ;; conj in "" in case we have dangling non-gen tempalte string as in
                    ;; "We start with {{generation}} conj in for 'interpose'"
                    (vec (re-seq gen-var-re template)) "")))]
    (->> elements
         (reduce
          (fn [[chat resolved-vars] part]
            (if (string/starts-with? part "{{")
              (let [gen-var (keyword
                             (string/replace
                              (second (re-find #"\{\{(.*?)\}\}" part))
                              #"\.\." "."))
                    ai      (assoc (gen-var generators) wkk/var-name gen-var)]
                [(if (gen-var resolved-vars)
                   (conj chat [:user part])
                   (conj chat [:assistant ai]))
                 (conj resolved-vars gen-var)])
              [(conj chat [:user part]) resolved-vars]))
          [[] #{}])
         first
         (partition-by first)
         (reduce (fn [chat elements]
                   (if (= :assistant (ffirst elements))
                     (into chat elements)
                     (conj chat [:user (apply str (mapv second elements))])))
                 []))))


(defn- split-gen-graph
  [graph]
  (reduce-kv
   (fn [[templates generators] k v]
     (if (map? v)
       [templates (assoc generators k v)]
       [(assoc templates k v) generators]))
   [{} {}]
   graph))


(defn- index-keys [index key]
  (filter key (keys index)))


(defn top-level-template
  [index context]
  (let [graph (reduce-kv
               (fn [m k v] (assoc m k (-> v keys set)))
               {}
               index)
        root-nodes
        (remove #(seq (index-keys graph %)) (->> graph vals (apply set/union)))]
    (select-keys context root-nodes)))

(defn- select-filled-in-parts
  "Given `generation-result` return sub map that contains no unfiled tempaltes"
  [generation-result]
  (select-keys
   generation-result
   (remove #(or (coll? (get generation-result %))
                (seq (selmer/known-variables (str (get generation-result %)))))
           (keys generation-result))))


(defn complete-graph
  "Completion case when we are processing prompt graph. Main work here is on constructing
  the output format with `usage` and `completions` sections."
  [llm-config graph vars-map]
  (let [[templates generators] (split-gen-graph graph)
        tpl-graph              (prep-graph templates vars-map)
        gen-env                (gen-environment llm-config tpl-graph vars-map)
        pre-gen-res            (p.eql/process gen-env vars-map (vec (keys tpl-graph)))]
    ;; Iterate over all paths in the prompt tree. Go from the root nodes we have to their bottom
    ;; leaves
    (loop [[[_top-name top-tpl] & tree-paths] (top-level-template
                                               (:com.wsscode.pathom3.connect.indexes/index-io gen-env)
                                               pre-gen-res)
           path-completions                   vars-map
           path-usage                         {}]
      (if top-tpl
        (let [top-template (chat llm-config (template->chat top-tpl generators) vars-map)
              gen-result   (merge
                            ;; render tempalates with newly available data
                            (reduce-kv (fn [m k v]
                                         (assoc m k (selmer/render
                                                     v
                                                     (completions top-template))))
                                       {}
                                       pre-gen-res)
                            (completions top-template))
              gen-usage    (usage top-template)]
          (recur tree-paths
                 ;; gather all the data points at each iterations
                 (merge
                  tpl-graph
                  path-completions
                  (select-filled-in-parts gen-result))
                 (merge path-usage gen-usage)))
        (u/mergex
         {completions path-completions}
         {usage path-usage})))))


(defn complete-template
  "Completion for a case when we have simple string `prompt`"
  [llm-config template vars-map]
  (get-in
   (complete-graph llm-config (append-generation-instruction template) vars-map)
   [completions default-template-completion]))


(defn generate
  "Generate completions for various modes. Generation mode is determined
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

  `env/config` holds configuration to make LLM calls and `inputs` has a data map
  for template slot filling."
  ([messages] (generate messages {}))
  ([messages inputs] (generate env/config messages inputs))
  ([env messages inputs]
   (let [start-time (u/now)
         gen-result (cond
                      (vector? messages) (chat env messages inputs)
                      (map? messages)    (complete-graph env messages inputs)
                      (string? messages) (complete-template env messages inputs))]
     (if (usage gen-result)
       (assoc gen-result
              :bosquet/time (- (u/now) start-time))
       gen-result))))

(comment
  (generate {:question-answer "Question: {{question}} Answer: {{answer}}"
             :answer          (llm :claude wkk/model-params {:model :claude-3-opus-20240229})}
            {:question "What is the distance from Moon to Io?"})

  (generate {:question-answer "Question: {{question}} Answer: {{answer}}"
             :answer          (llm :ollama wkk/model-params {:model :zephyr :max-tokens 50})
             :self-eval       ["{{question-answer}}"
                               "Is this a correct answer?"
                               "{{test}}"]
             :test            (llm :ollama wkk/model-params {:model :zephyr :max-tokens 50})}
            {:question "What is the distance from Moon to Io?"})
  (generate
   {:sys "Calc:"
    :a   "{{sys}} {{M}}+2={{x}}"
    :b   "Result is: {{a}}"
    :x   (llm :ollama wkk/model-params {:model :llama3:8b})
    :y   (llm :ollama wkk/model-params {:model :llama3:8b})}
   {:M 10 :N 5})

  (generate
   {:sys "Calc:"
    :a   "{{sys}} {{M}}+2={{x}}"
    :b   "{{sys}} {{N}}-1={{y}}"
    :x   (llm :ollama wkk/model-params {:model :zephyr})
    :y   (llm :ollama wkk/model-params {:model :zephyr})}
   {:M 10 :N 5})

  (generate
   {:sys "Calc:"
    :a   "{{sys}} {{M}}+2={{x}}"
    :x   (llm :ollama wkk/model-params {:model :zephyr})}
   {:M 10})

  (generate
   {:question-answer "Question: {{question}}  Answer: {{answer}}"
    :answer          (llm :mistral-small)
    :self-eval       ["{{question-answer}}"
                      "Is this a correct answer?"
                      "{{test}}"]
    :test            (llm :mistral-medium)}
   {:question "What is the distance from Moon to Io?"})

  (generate
   (str
    "Extract name from this text. "
    "TEXT: Į Europos čempionatus išleidęs "
    "Laimutį Adomaitį, Aldą Lukošaitį, Vladimiras sako, dirbs ir toliau ne vien todėl, "
    "kad tai mylimas darbas."))


  (generate
   "When I was {{age}} my sister was half my age. Now I’m 70 how old is my sister?"
   {:age 13})

  (def solver (llm :openai wkk/model-params {:model :gpt-4 :max-tokens 50}))

  (def g {:calc       ["Lets solve math problems."
                       "Answer only with calculated result. Abstain from explanations or rephrasing the task!"
                       "You are given the values:"
                       "A = {{a}}; B = {{b}}; C = {{c}}"
                       "Solve the following equations:"
                       "{{tasks}}"
                       "{{grade}}"]
          :tasks      ["{{p1}}" "{{p2}}" "{{p3}}"]
          :p1         "A + B = {{x}}"
          :p2         "A - B = {{y}}"
          :p3         "({{x}} + {{y}}) / C = {{z}}"
          :eval1-role ["{{tasks}}"
                       "Evaluate if the solutions to the above equations are correct"
                       "{{eval1}}"]
          :eval2-role ["{{tasks}}"
                       "Evaluate if the solutions to the above equations are calulated optimaly"
                       "{{eval2}}"]
          :grade      ["Based on the following evaluations to math problems:"
                       "Evaluation A: {{eval1-role}}"
                       "Evaluation B: {{eval2-role}}"
                       "Based on this work grade (from 1 to 10) student's math knowledge."
                       "Give only grade number like '7' abstain from further explanations."
                       "{{score}}"]
          :x          solver
          :y          solver
          :z          solver
          :eval1      (llm :mistral wkk/model-params {:model :mistral-small :max-tokens 50})
          :eval2      (llm :mistral wkk/model-params {:model :mistral-small :max-tokens 50})
          :score      (llm :openai
                           wkk/output-format :number
                           wkk/model-params {:model :gpt-4 :max-tokens 2})})

  (generate g {:a 5 :b 2 :c 1})

  (generate
   {:q1   ["Q: When I was {{age}} my sister was half my age. Now I’m 70 how old is my sister? A: {{a}}"]
    :a    (llm :mistral-small)}
   {:age 10})

  (generate [[:system "You are an amazing writer."]
             [:user ["Write a synopsis for the play:"
                     "Title​ {{title}}"
                     "Genre​ {{genre}}"
                     "Synopsis:"]]
             [:assistant (llm wkk/openai
                              wkk/model-params {:model :gpt-4 :temperature 0.8 :max-tokens 120}
                              wkk/var-name :synopsis)]
             [:user "Now write a critique of the above synopsis:"]
             [:assistant (llm wkk/openai
                              wkk/var-name :critique)]]
            {:title "Mr. X"
             :genre "Sci-Fi"})

  (generate
   [[:system ["As a brilliant astronomer, list distances between planets and the Sun"
              "in the Solar System. Provide the answer in EDN map where the key is the"
              "planet name and the value is the string distance in millions of kilometers."
              "{{analysis}}"]]
    [:user ["Generate only EDN omit any other prose and explanations."]]
    [:assistant (llm :gpt-4
                     wkk/var-name :distances
                     wkk/output-format :edn
                     wkk/model-params {:max-tokens 300})]
    [:user ["Based on the EDN distances data"
            "provide me with​ a) average distance b) max distance c) min distance"]]
    [:assistant (llm :mistral-small
                     wkk/var-name :analysis)]])
  #__)
