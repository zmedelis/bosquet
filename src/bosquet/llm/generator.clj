(ns bosquet.llm.generator
  (:require
   [bosquet.converter :as converter]
   [bosquet.db.cache :as cache]
   [bosquet.env :as env]
   [bosquet.llm :as llm]
   [bosquet.llm.gen-data :as gd]
   [bosquet.llm.gen-tree :as gen-tree]
   [bosquet.llm.wkk :as wkk]
   [bosquet.template.selmer :as selmer]
   [bosquet.utils :as u]
   [com.wsscode.pathom3.connect.indexes :as pci]
   [com.wsscode.pathom3.connect.operation :as pco]
   [com.wsscode.pathom3.entity-tree :as pet]
   [com.wsscode.pathom3.interface.smart-map :as psm]
   [com.wsscode.pathom3.plugin :as p.plugin]
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

(def text-trail
  :bosquet.generation/text-trail)

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
  (if (map? properties)
    (let [format-fn      (partial converter/coerce (wkk/output-format properties))
          service-config (dissoc (llm-impl llm-config) wkk/gen-fn wkk/chat-fn)
          chat-fn        (partial (get-in llm-config [llm-impl wkk/chat-fn]) service-config)
          params         (assoc model-params :messages (->chatml messages))
          result         (if use-cache
                           (cache/lookup-or-call chat-fn params)
                           (chat-fn params))]
      (update-in result [wkk/content :content] format-fn))
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
  (timbre/infof "'%s' resolver for IN: '%s', OUT: '%s'" name-sufix input [message-key])
  (pco/resolver
   {::pco/op-name (-> message-key .-sym (str "-" name-sufix) symbol)
    ::pco/output  [message-key]
    ::pco/input   input
    ::pco/resolve resolve-fn}))

(defn find-refering-templates
  "Given all the templates in a `context-map` find out which ones are
  have references to `var-name`"
  [var-name context-map]
  (reduce-kv
   (fn [refs tpl-name tpl]
     (if (and (string? tpl) (contains? (-> tpl selmer/known-variables-in-order set) var-name))
       (conj refs tpl-name)
       refs))
   []
   context-map))

(defn- update-text-trail
  [{entry-tree ::pet/entity-tree*} text]
  (swap! entry-tree assoc text-trail (str (text-trail @entry-tree) text)))

(defn- current-text-trail
  [{entry-tree ::pet/entity-tree*}]
  (get @entry-tree text-trail))

(defn- entry
  [{entry-tree ::pet/entity-tree*} entry-key]
  (get @entry-tree entry-key))

(selmer/set-missing-value-formatter)

(defn- render
  [{entry-tree ::pet/entity-tree*} content]
  (selmer/render content
                 (merge
                  @entry-tree
                  (gd/reduce-gen-graph
                   (fn [m k v] (assoc m k
                                      (completions v)))
                   @entry-tree))))

(defn- generation-resolver
  [llm-config message-key context vars-map]
  (let [content-or-llm-cfg (message-key context)]
    (if (map? content-or-llm-cfg)
      ;; Generation node
      (mapv
       (fn [refering-template-key]
         (->resolver (str "ai-gen-" (name refering-template-key)) message-key
                     [refering-template-key]
                     (fn [env _input]
                       (try
                         (let [txt (render
                                    env
                                    (selmer/clear-gen-var-slot
                                     (entry env refering-template-key)
                                     message-key))
                               #_(-> env current-text-trail (selmer/clear-gen-var-slot message-key))
                               {gen-usage wkk/usage
                                {gen-content :content} wkk/content}
                               (call-llm llm-config content-or-llm-cfg [[:user txt]])]
                           (update-text-trail env gen-content)
                           {message-key {completions gen-content
                                         usage       gen-usage}})
                         (catch Exception e
                           (timbre/error e))))))
       (find-refering-templates message-key context))

      ;; Template node
      (let [;; fill in provided data slots, no need to go over those with resolvers
            message-content (selmer/render content-or-llm-cfg vars-map)]
        (try
          (->resolver "template" message-key (vec (filter #(string? (get context %))
                                                          (selmer/known-variables-in-order message-content)))
                      (fn [{entry-tree ::pet/entity-tree* :as env} _input]
                        (let [result (selmer/render message-content
                                                    (merge
                                                     @entry-tree
                                                     (gd/reduce-gen-graph
                                                      (fn [m k v] (assoc m k
                                                                         (completions v)))
                                                      @entry-tree)))]
                          (update-text-trail env result)
                          {message-key result})))
          (catch Exception e
            (timbre/error e)))))))

(defn- prompt-indexes [llm-config context vars-map]
  (pci/register
   (mapv
    (fn [prompt-key]
      (generation-resolver llm-config prompt-key context vars-map))
    (keys context))))

(defn append-generation-instruction
  "If template does not specify generation function append the default one."
  [string-template]
  {default-template-prompt     (selmer/append-slot string-template default-template-completion)
   default-template-completion (llm (env/default-llm))})

(defn complete
  "Complete the template graph case, where execution order will be determined
  by Pathom."
  [llm-config context vars-map]
  (-> (prompt-indexes llm-config context vars-map)
      (psm/smart-map vars-map)
      (resolver-error-wrapper)
      (select-keys (conj (keys context) text-trail))))

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
       ;; two reduce is crap, but then there fundamental issues with deps
       ;; inside for loops and so on
       (reduce-kv
        (fn [m k v]
          (assoc m k (if (string? v)
                       (selmer/render v available-data)
                       v)))
        {})))

(defn complete-graph
  "Completion case when we are processing prompt graph. Main work here is on constructing
  the output format with `usage` and `completions` sections."
  [llm-config graph vars-map]
  (let [graph       (-> graph (prep-graph vars-map) gen-tree/expand-dependencies)
        gen-result  (complete llm-config graph vars-map)
        gen-usage   (gd/reduce-gen-graph (fn [m k v] (assoc m k (usage v))) gen-result)
        total-usage (gd/total-usage gen-usage)]
    (u/mergex
     {completions
      (gen-tree/collapse-resolved-tree
       (reduce-kv
        (fn [m k v]
          (assoc m k
                 (if (map? v)
                   (completions v)
                   (selmer/render
                    v
                    (gd/reduce-gen-graph
                     (fn [m k _v]
                       (assoc m k (get-in gen-result [k completions])))
                     gen-result)))))
        {} gen-result))}
     {usage (when (seq total-usage) (assoc gen-usage :bosquet/total total-usage))})))

(defn complete-template
  "Completion for a case when we have simple string `prompt`"
  [llm-config template vars-map]
  (get-in
   (complete-graph llm-config (append-generation-instruction template) vars-map)
   [completions default-template-completion]))

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
     (map? messages)    (complete-graph llm-config messages vars-map)
     (string? messages) (complete-template llm-config messages vars-map))))

(comment

  (generate "When I was 6 my sister was half my age. Now I’m 70 how old is my sister?")

  (generate
   "When I was {{age}} my sister was half my age. Now I’m 70 how old is my sister?"
   {:age 13})

  (generate
   {:role  ["Lets play a calculator. Solve the following equations:"
            "{{part1}}"
            "{{part2}}"]
    :part1 "{{a}} + {{b}} = {{x}}"
    :part2 "{{x}} - {{c}} = {{y}}"
    :x     (llm :openai)
    :y     (llm :openai)}
   {:a 2 :b 4 :c 1})

  (generate
   {:synopsis ["You are a playwright. Given the play's title and it's genre"
               "it is your job to write synopsis for that play."
               "Title: {{title}}"
               "Genre: {{genre}}"
               ""
               "Synopsis: {{play}}"]
    :play     (llm :openai)}
   {:title "City of Shadows" :genre "crime"})

  (generate
   {:q1   ["Q: When I was {{age}} my sister was {{age-relation}} my age. Now I’m 70 how old is my sister? A: {{a}}"
           "Is this answer correct? {{eval}}"]
    :eval (llm wkk/openai)
    :a    (llm wkk/openai wkk/model-params {:max-tokens 100})}
   {:age          13
    :age-relation "half"})

  (gen-tree/expand-dependencies
   (prep-graph
    {:q1   ["Q: When I was {{age}} my sister was half my age. Now I’m 70 how old is my sister? A: {{a}}"
            "Is this answer correct? {{eval}}"]
     :eval (llm wkk/lmstudio)
     :a    (llm wkk/lmstudio wkk/model-params {:max-tokens 250})}))

  (generate
   [[:system "You are an amazing writer."]
    [:user ["Write a synopsis for the play:"
            "Title​ {{title}}"
            "Genre​ {{genre}}"
            "Synopsis:"]]
    [:assistant (llm wkk/lmstudio
                     wkk/model-params {:temperature 0.8 :max-tokens 120}
                     wkk/var-name :synopsis)]
    [:user "Now write a critique of the above synopsis:"]
    [:assistant (llm wkk/lmstudio
                     wkk/model-params {:temperature 0.2 :max-tokens 120}
                     wkk/var-name     :critique)]]
   {:title "Mr. X"
    :genre "Sci-Fi"})

  (generate
   llm/default-services
   {:question-answer "Question​ {{question}}  Answer: {{answer}}"
    :answer          (llm wkk/lmstudio wkk/cache false)
    :self-eval       ["{{question-answer}}"
                      ""
                      "Is this a correct answer?"
                      "{{test}}"]
    :test            (llm wkk/lmstudio)}
   {:question "What is the distance from Moon to Io?"})

  (generate
   {:astronomy ["As a brilliant astronomer, list distances between planets and the Sun"
                "in the Solar System. Provide the answer in JSON map where the key is the"
                "planet name and the value is the string distance in millions of kilometers."
                "Generate only JSON omit any other prose and explanations."]
    :distances (llm wkk/lmstudio
                    wkk/output-format :json
                    wkk/model-params {:max-tokens 300}
                    wkk/context :astronomy)
    :analysis  ["Based on the JSON planet to sun distances table"
                "provide me with​ a) average distance b) max distance c) min distance"
                "{{distances}}"]
    :stats     (llm wkk/lmstudio
                    wkk/context :analysis)})

  ;; ----
  (generate
   {:q
    "Q: When I was {{age}} my sister was half my age. Now I’m 70 how old is my sister?
     A: {{a}}"
    :a (llm wkk/lmstudio
            wkk/model-params {:max-tokens 50})}
   {:age 13})

  (generate
   [[:system "You are a calculator."]
    [:user "2-2="]
    [:assistant (llm wkk/cohere
                     wkk/model-params {:temperature 0.0 :max-tokens 10}
                     wkk/var-name :calc)]])
  #__)
