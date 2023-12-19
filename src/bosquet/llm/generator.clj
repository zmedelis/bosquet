(ns bosquet.llm.generator
  (:require
   [bosquet.complete :as complete]
   [bosquet.llm :as llm]
   [bosquet.llm.chat :as chat]
   [bosquet.template.read :as template]
   [bosquet.template.tag :as tag]
   [bosquet.wkk :as wkk]
   [com.wsscode.pathom3.connect.indexes :as pci]
   [com.wsscode.pathom3.connect.operation :as pco]
   [com.wsscode.pathom3.interface.smart-map :as psm]
   [com.wsscode.pathom3.plugin :as p.plugin]
   [taoensso.timbre :as timbre]))

(tag/add-tags)

(defn- has-gen-tag? [template]
  (re-find #"\{%\s+gen\s+%\}" template))

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
   (let [template (if (has-gen-tag? template) template (str template " {% gen %}"))]
     (template/fill-slots
      template
      (assoc slots :the-key (first (template/generation-vars template))) ; only one `gen` is supported in template
      config))))

(defn output-keys [k template]
  (cons k (template/generation-vars template)))

(defn- generation-resolver
  "Build dynamic resolvers figuring out what each prompt tempalte needs
  and set it as required inputs for the resolver.

  For the output check if themplate is producing generated content
  anf if so add a key for it into the output"
  [the-key template system-config]
  (let [input  (vec (template/slots-required template))
        output (into input (output-keys the-key template))]
    (timbre/info "Resolver: " the-key)
    (timbre/info "  Input: " input)
    (timbre/info "  Output: " output)
    (pco/resolver
     {::pco/op-name (-> the-key .-sym (str "-gen") keyword symbol)
      ::pco/output  output
      ::pco/input   input
      ::pco/resolve
      (fn [_env input]
        (let [[completed completion] (template/fill-slots template
                                       ;; TODO refactor out `the-key`
                                                          (assoc input :the-key the-key)
                                                          system-config)]
          (merge {the-key completed} completion input)))})))


(defn- generation-resolver-2
  "Build dynamic resolvers figuring out what each prompt tempalte needs
  and set it as required inputs for the resolver.

  For the output check if themplate is producing generated content
  anf if so add a key for it into the output"
  [llm-config properties current-prompt template]
  (let [{:keys [data-vars gen-vars]} (template/template-vars template)]
    (timbre/info "Resolving: " current-prompt)
    (timbre/info "\tInput data: " data-vars)
    (timbre/info "\tGenerate for: " gen-vars)
    (pco/resolver
     {::pco/op-name (-> current-prompt .-sym (str "-gen") keyword symbol)
      ::pco/output  gen-vars
      ::pco/input   data-vars
      ::pco/resolve
      (fn [_env input]
        (let [[completed completion] (template/fill-slots-2
                                      llm-config properties template input)
              completed              ((first gen-vars) completed)
              completion             (:gen2 completion)]
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

(defn- prompt-indexes [prompts opts]
  (pci/register
   (mapv
    (fn [prompt-key]
      (generation-resolver prompt-key (prompt-key prompts) opts))
    (keys prompts))))

(defn- prompt-indexes-2 [llm-config opts prompts]
  (pci/register
   (mapv
    (fn [prompt-key]
      (generation-resolver-2 llm-config opts prompt-key (prompt-key prompts) ))
    (keys prompts))))

(defn all-keys
  "Produce a list of all the data keys that will come out of the Pathom processing.
  Whatever is refered in `prompts` and comes in via input `data`"
  [prompts data]
  (into
   (vec (keys data))
   (mapcat
    (fn [prompt-key]
      (output-keys prompt-key (get prompts prompt-key)))
    (keys prompts))))

(defn all-keys2
  [prompts data]
  (reduce
   (fn [m prompt-key]
     (concat m
             (:gen-vars (template/template-vars (get prompts prompt-key)))))
   (vec (keys data))
   (keys prompts)))

(defn generate
  ([prompts] (generate prompts nil nil))
  ([prompts inputs] (generate prompts inputs nil))
  ([prompts inputs config]
   ;; If we get string for `prompts` then we assume it is a single prompt, send it to
   ;; `complete-template` and return the result. Additionaly if the prompt does not have
   ;; `gen` tag attach it to the end.
   (if (string? prompts)
     (let [[completed completion] (complete-template prompts inputs config)]
       (merge completion inputs {:bosquet.gen/completed-prompt completed}))
     (let [extraction-keys (all-keys prompts inputs)]
       (timbre/info "Resolving for: " extraction-keys)
       (-> (prompt-indexes prompts config)
           (resolver-error-wrapper)
           (psm/smart-map inputs)
           (select-keys extraction-keys))))))

(defn generate-2
  [llm-provider-config
   gen-props
   context
   input-data]
  (if (string? context)
    (let [[completed completion] (complete-template context input-data llm-provider-config)]
      (merge completion input-data {:bosquet.gen/completed-prompt completed}))
    (let [extraction-keys (all-keys2 context input-data)]
      (timbre/info "Resolving for: " extraction-keys)
      (-> (prompt-indexes-2 llm-provider-config gen-props context)
          (resolver-error-wrapper)
          (psm/smart-map input-data)
          (select-keys extraction-keys)))))

(defn- fill-converation-slots
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

(defn chat
  ([messages] (chat messages nil))
  ([messages inputs] (chat messages inputs nil))
  ([messages inputs opts]
   (let [updated-context (fill-converation-slots messages inputs opts)]
     (complete/chat-completion updated-context opts))))

(defn ->completer
  [llm-services]
  (fn [parameters context data]
    (generate-2 llm-services parameters context data)))

(comment

  (def generator
    (->completer
     (merge
      llm/default-services
      {llm/cohere {:api-key      (-> "config.edn" slurp read-string :cohere-api-key)
                   :api-endpoint "https://api.openai.com/v1"
                   llm/chat-fn   llm/handle-cohere-chat}
       :local2    {llm/complete-fn (fn [_system _options] "COMPLETE-LOCAL2")
                   llm/chat-fn     (fn [_system _options]
                                     (prn "LOCAL")
                                     {bosquet.llm.llm/content
                                      {:completion
                                       {:content "CHAT-LOCAL2"}}})}})))

  ;; COMPLETION
  (generator
   {:answer {llm/service      llm/openai
             :cache           true
             llm/model-params {:model :gpt-3.5-turbo}}
    :eval   {llm/service :local2}}

   {:question-answer "Question: {{question}}  Answer: {% gen2 answer %}"
    :self-eval       "{{answer}} Is this a correct answer? {% gen2 eval%}"}

   {:question "What is the distance from Moon to Io?"})

  ;; CHAT
  (generator
   {:answer {llm/service      llm/openai
             llm/model-params {:temperature 0.4
                               :model       :gpt-3.5-turbo}}
    :eval   {llm/service :local2
             :cache      true}}

   [:system "You are a playwright. Given the play's title and genre write synopsis."
    :user "Title: {{title}}; Genre: {{genre}}"
    :user "Playwright: This is a synopsis for the above play:"]

   {:title "Mr. X" :genre "crime"})

  ;; TEMPLATE
  (generator
   {:answer {llm/service      llm/openai
             llm/model-params {:temperature 0.4
                               :model       :gpt-3.5-turbo}}
    :eval   {llm/service :local2
             :cache      true}}

   "Question: {{question}}  Answer: {{answer}}"

   {:question "What is the distance from Moon to Io?"})

  (generate-2
   {llm/openai {:api-key      (-> "config.edn" slurp read-string :openai-api-key)
                :api-endpoint "https://api.openai.com/v1"}
    :local     {llm/gen-fn (fn [_system options] {:eval (str "TODO-" (:gen options) "-TODO")})}}
   {:answer {llm/service      llm/openai
             llm/model-params {:temperature 0.4
                               :model       :gpt-3.5-turbo}}
    :eval   {llm/service :local
             :cache      true}}
   {:question-answer "Question: {{question}}  Answer: {% gen2 answer %}"
    :self-eval       "{{answer}} Is this a correct answer? {% gen2 eval %}"}
   {:question "What is the distance from Moon to Io?"})

  (generate
   {:question-answer "Question: {{question}}  Answer: {% gen var-name=answer %}"}
   {:question "What is the distance from Moon to Io?"}
   {:answer {wkk/service          :llm/openai
             wkk/model-parameters {:max-tokens 100}}})

  (chat
   [(chat/speak chat/system "You are a brilliant {{role}}.")
    (chat/speak chat/user "What is a good {{meal}}?")
    (chat/speak chat/assistant "Good {{meal}} is a {{meal}} that is good.")
    (chat/speak chat/user "Help me to learn the ways of a good {{meal}}.")]
   {:role "cook" :meal "cake"}
   {chat/conversation
    {wkk/service          :llm/cohere
     wkk/model-parameters {:temperature 0
                           :max-tokens  100}}})

  (generate
   {:role            "As a brilliant {{you-are}} answer the following question."
    :question        "What is the distance between Io and Europa?"
    :question-answer "{{role}} Question: {{question}}  Answer: {% gen var-name=answer %}"
    :self-eval       "{{answer}} Is this a correct answer? {% gen var-name=test %}"}
   {:you-are  "astronomer"
    :question "What is the distance from Moon to Io?"}
   {:answer {wkk/service          :llm/cohere
             wkk/model-parameters {:max-tokens 100}}
    :test   {wkk/service          :llm/cohere
             wkk/model-parameters {:max-tokens 100}}})

  (generate
   "As a brilliant {{you-are}} list distances between planets and the Sun
in the Solar System. Provide the answer in JSON map where the key is the
planet name and the value is the string distance in millions of kilometers. Generate only JSON
omit any other prose and explanations."
   {:you-are "astronomer"}
   {:gen {wkk/output-format :json}})

  (generate
   {:role            "As a brilliant {{you-are}} answer the following question."
    :question-answer "Question: {{question}}  Answer: {% gen var-name=answer %}"}
   {:you-are  "astronomer"
    :question "What is the distance from Moon to Io?"}
   {:answer {wkk/service          :llm/openai
             wkk/cache            true
             wkk/model-parameters {:temperature 0.4 :model "gpt-3.5-turbo"}}})

  (chat
   (chat/converse
    chat/system "You are a playwright. Given the play's title and genre write synopsis."
    chat/user "Title: {{title}}; Genre: {{genre}}"
    chat/user "Playwright: This is a synopsis for the above play:")
   {:title "Mr. X" :genre "crime"}
   {chat/conversation
    {wkk/service          :llm/lmstudio
     wkk/model-parameters {:temperature 0.0 :max-tokens 100}}})

  (complete-template
   "You are a playwright. Given the play's title and it's genre write synopsis for that play.
     Title: {{title}}
     Genre: {{genre}}
     Playwright: This is a synopsis for the above play: {% gen var-name=text %}"
   {:title "Mr. X" :genre "crime"}
   {:text {wkk/service          wkk/oai-service
           wkk/cache            false
           wkk/model-parameters {:temperature 0.0 :max-tokens 100 :model "gpt-3.5-turbo-1106"}}})

  (generate
   "As a brilliant {{you-are}} list distances between planets and the Sun
      in the Solar System. Provide the answer in JSON map where the key is the
      planet name and the value is the string distance in millions of kilometers.
      Generate only JSON omit any other prose and explanations."
   {:you-are "astronomer"}
   {:gen {wkk/service       :llm/lmstudio
          wkk/output-format :json
          :api-endpoint     "http://localhost:1235/v1"}})

  (chat
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
