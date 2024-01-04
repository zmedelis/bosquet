(ns bosquet.llm.generator
  (:require
   [bosquet.converter :as converter]
   [bosquet.llm :as llm]
   [bosquet.template.read :as template]
   [bosquet.template.tag :as tag]
   [bosquet.utils :as u]
   [clojure.string :as string]
   [com.wsscode.pathom3.connect.indexes :as pci]
   [com.wsscode.pathom3.connect.operation :as pco]
   [com.wsscode.pathom3.interface.smart-map :as psm]
   [com.wsscode.pathom3.plugin :as p.plugin]
   [selmer.parser :as selmer]
   [taoensso.timbre :as timbre]))

(tag/add-tags)

(defn complete-template
  "Fill in `template` `slots` with Selmer and call generation function
  (if present) to complete the text.

  This is a sole template based generation bypasing Pathom resolver figuring out
  the sequence of multiple template gen calls. Hence the parameter is not
  a map with multiple templates but a single template string.

  If `template` has no `gen` tag, then it will be added to the end of the template.

  Likely to be refactored away in the future versions in favour of a single
  `complete` entry point.

  Note that `template` can have only one `gen` call.

  OK : 'Generate a joke about {{topic}}. {% gen var-name=joke %}'
  BAD: 'Generate a joke about {{topic}}. {% gen var-name=joke %} and {% gen var-name=another-joke %'"
  ([template] (complete-template template nil nil))
  ([template slots] (complete-template template slots {}))
  ([template slots config]
   #_(let [template (template/ensure-gen-tag template)]
       (template/fill-slots-2
        template
        (assoc slots :the-key (first (template/generation-vars template))) ; only one `gen` is supported in template
        config))))

#_(defn- generation-resolver
    "Build dynamic resolvers figuring out what each prompt tempalte needs
  and set it as required inputs for the resolver.

  For the output check if themplate is producing generated content
  anf if so add a key for it into the output"
    [llm-config properties current-prompt data-input-vars template]
    (let [{:keys [data-vars gen-vars]} (template/template-vars template)
        ;; This set intersection is needed because some of the data vars might not be in
        ;; supplied data map, values might be omited and come from specified defaults in
        ;; Selmer template
          data-vars                    (vec (set/intersection (set data-input-vars) (set data-vars)))]
      (timbre/info "Resolving: " current-prompt)
      (timbre/info "\tInput data: " data-vars)
      (timbre/info "\tGenerate for: " gen-vars)
      (pco/resolver
       {::pco/op-name (-> current-prompt .-sym (str "-gen") keyword symbol)
        ::pco/output  gen-vars
        ::pco/input   data-vars
        ::pco/resolve
        (fn [_env input]
          (let [current-gen-var        (first gen-vars)
                [completed completion] (template/fill-slots-2 llm-config properties template input)
                completed              (current-gen-var completed)
                completion             (:gen completion)]
            (merge {current-prompt completed} completion input)))})))

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

#_(defn- prompt-indexes-2 [llm-config opts data-vars prompts]
    (pci/register
     (mapv
      (fn [prompt-key]
        (generation-resolver llm-config opts prompt-key data-vars (prompt-key prompts)))
      (keys prompts))))

#_(defn all-keys2
    [prompts data]
    (u/flattenx
     (concat
      (keys data)
      (w/prewalk
       #(cond
          (string? %) (:gen-vars (template/template-vars %))
        ;; TODO; this is OAI specific, and not really a good place to do this
          (#{:system :assistant :user} %) nil
          :else %)
       prompts))))

#_(defn- complete*
    [llm-provider-config gen-props context input-data]
    (let [extraction-keys (all-keys2 context input-data)]
      (-> (prompt-indexes-2 llm-provider-config gen-props (keys input-data) context)
          (resolver-error-wrapper)
          (psm/smart-map input-data)
          (select-keys extraction-keys))))

#_(defn- fill-converation-slots-2
    [llm-provider-config gen-props context input]
    (mapv
     (fn [{content chat/content :as msg}]
       (assoc msg
              chat/content
              (first (template/fill-slots-2 llm-provider-config gen-props
                                            content
                                            input))))
     context))

#_(defn- chat* [llm-provider-config gen-props context input]
    (let [updated-context (fill-converation-slots-2 llm-provider-config gen-props context input)
          service-config (get llm-provider-config (llm/service gen-props))
          {:llm/keys [chat-fn]} service-config]
      (->
       (chat-fn (dissoc service-config llm/gen-fn llm/chat-fn)
                (assoc gen-props :messages updated-context))
       bosquet.llm.llm/content
       :completion)))

#_(defn- fill-converation-slots
    "Fill all the Selmer slots in the conversation context. It will
  check all roles and fill in `{{slots}}` from the `inputs` map."
    [messages inputs opts]
  ;; TODO run `generate` over all the conversation-context to fill in the slots
    (mapv
     (fn [{content chat/content :as msg}]
       (assoc msg
              chat/content
              (first (template/fill-slots content inputs opts))))
     messages))

#_(defn chat
    ([messages] (chat messages {}))
    ([messages inputs] (chat messages inputs {}))
    ([messages inputs opts]
     (let [updated-context (fill-converation-slots messages inputs opts)]
       (complete/chat-completion updated-context opts))))

#_(defn generate
    ([context]
     (generate {llm/service llm/openai} context))

    ([gen-props context]
     (generate gen-props context nil))

    ([gen-props context input-data]
     (generate llm/default-services gen-props context input-data))

    ([llm-provider-config gen-props context input-data]
     (if (vector? context)
       (chat* llm-provider-config gen-props (apply chat/converse context) input-data)
       (complete* llm-provider-config gen-props
                  (if (string? context)
                  ;; a single string template prompt, convert to map context
                  ;; to be processed as completion
                    {:string-template (template/ensure-gen-tag context)}
                    context)
                  input-data))))

#_(defn ->completer
    [llm-services]
    (fn [parameters context data]
      (generate llm-services parameters context data)))

;;  -- V3 --

(defn ->chatml [messages]
  (map
   (fn [[role content]] {:role role :content content})
   (partition 2 messages)))

(defn- call-llm [llm-config properties messages]
  (if (map? properties)
    (try
      (let [llm-impl       (llm/service properties)
            format         (partial converter/coerce (llm/output-format properties))
            model-params   (llm/model-params properties)
            chat-fn        (get-in llm-config [llm-impl llm/chat-fn])
            service-config (dissoc (llm-impl llm-config) llm/gen-fn llm/chat-fn)
            messages       (->chatml messages)
            result         (chat-fn service-config (assoc model-params :messages messages))]
        (format
         (get-in result
                 [:bosquet.llm.llm/content :completion :content])))
      (catch Exception e
        (timbre/error e)))
    (timbre/warnf ":assistant instruction does not contain AI gen function spec")))

(defn- join
  [content]
  (if (coll? content) (string/join "\n" content) content))

(defn chat
  [llm-config messages vars-map]
  (loop [[role content & messages] messages
         processed-messages        []
         ctx                       vars-map]
    (if (nil? role)
      {:bosquet/conversation    processed-messages
       :bosquet/completions (apply dissoc ctx (keys vars-map))}
      (if (= :assistant role)
        (let [gen-result (call-llm llm-config content processed-messages)
              var-name   (llm/var-name content)]
          (recur messages
                 (into processed-messages [role gen-result])
                 (assoc ctx var-name gen-result)))
        (let [tpl-result (first (template/render (join content) ctx))]
          (recur messages
                 (into processed-messages [role tpl-result])
                 ctx))))))

(defn- generation-resolver
  [llm-config message-key {ctx-var llm/context :as message-content}]
  (if (map? message-content)
    (if ctx-var
      (pco/resolver
       {::pco/op-name (-> message-key .-sym (str "-ai-gen") keyword symbol)
        ::pco/output  [message-key]
        ::pco/input   [(llm/context message-content)]
        ::pco/resolve
        (fn [{entry-tree :com.wsscode.pathom3.entity-tree/entity-tree*} _input]
          (try
            (let [full-text (get @entry-tree (llm/context message-content))
                  result    (call-llm llm-config message-content [:user full-text])]
              {message-key result})
            (catch Exception e
              (timbre/error e))))})
      (timbre/warnf "Context var is not set in generation spec. Add 'llm/context' to '%s'" message-key)
      )
    ;; TEMPLATE
    (let [message-content (join message-content)]
      (pco/resolver
       {::pco/op-name (-> message-key .-sym (str "-template") keyword symbol)
        ::pco/output  [message-key]
        ::pco/input   (vec (selmer/known-variables message-content))
        ::pco/resolve
        (fn [{entry-tree :com.wsscode.pathom3.entity-tree/entity-tree*} _input]
          {message-key (first (template/render message-content @entry-tree))})}))))

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
  (let [vars-map (merge vars-map {:bosquet/full-text (atom "")})
        indexes (prompt-indexes llm-config messages)
        sm (psm/smart-map indexes vars-map)]
    (select-keys sm (keys messages))))

(defn append-generation-instruction
  [string-template]
  {:prompt     string-template
   :completion {llm/service :openai
                llm/context :prompt}})

(defn generate
  ([messages] (generate llm/default-services messages {}))
  ([messages vars-map]
   (generate llm/default-services messages vars-map))
  ([llm-config messages vars-map]
   (cond
     (vector? messages) (chat llm-config messages vars-map)
     (map? messages)    (complete llm-config messages vars-map)
     (string? messages) (:completion (complete llm-config (append-generation-instruction messages) vars-map)))))

(defn llm
  "A helper function to create LLM spec for calls during the generation process.
  It comes back with a map constructed from `service` and `args`:

  ```
  {:llm/service      service
   :llm/
   :llm/model-params params}
  ```"
  [service & args]
  (assoc (apply hash-map args) llm/service service))

(comment

  (generate
   "When I was 6 my sister was half my age. Now I’m 70 how old is my sister?")

  (generate
   "When I was {{age}} my sister was half my age. Now I’m 70 how old is my sister?"
   {:age 13})

  (generate
   [:system "You are an amazing writer."
    :user ["Write a synopsis for the play:"
           "Title: {{title}}"
           "Genre: {{genre}}"
           "Synopsis:"]
    :assistant (llm :openai
                    llm/model-params {:temperature 0.8 :max-tokens 120}
                    llm/var-name :synopsis)
    :user "Now write a critique of the above synopsis:"
    :assistant (llm :openai
                    llm/model-params {:temperature 0.2 :max-tokens 120}
                    llm/var-name     :critique)]
   {:title "Mr. X"
    :genre "Sci-Fi"})

  (generate
   llm/default-services
   {:question-answer "Question: {{question}}  Answer:"
    :answer          (llm :openai llm/context :question-answer)
    :self-eval       ["Question: {{question}}"
                      "Answer: {{answer}}"
                      ""
                      "Is this a correct answer?"]
    :test            (llm :openai llm/context :self-eval)}
   {:question "What is the distance from Moon to Io?"})

  (generate
   {:astronomy (u/join-nl
                "As a brilliant astronomer, list distances between planets and the Sun"
                "in the Solar System. Provide the answer in JSON map where the key is the"
                "planet name and the value is the string distance in millions of kilometers."
                "Generate only JSON omit any other prose and explanations.")
    :answer    (llm :openai
                    llm/output-format :json
                    llm/context :astronomy)})

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
