(ns bosquet.llm.generator
  (:require
   [bosquet.complete :as complete]
   [bosquet.llm :as llm]
   [bosquet.llm.chat :as chat]
   [bosquet.template.read :as template]
   [bosquet.template.tag :as tag]
   [bosquet.utils :as u]
   [bosquet.wkk :as wkk]
   [clojure.walk :as w]
   [com.wsscode.pathom3.connect.indexes :as pci]
   [com.wsscode.pathom3.connect.operation :as pco]
   [com.wsscode.pathom3.interface.smart-map :as psm]
   [com.wsscode.pathom3.plugin :as p.plugin]
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
   (let [template (template/ensure-gen-tag template)]
     (template/fill-slots-2
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

(defn- prompt-indexes-2 [llm-config opts prompts]
  (pci/register
   (mapv
    (fn [prompt-key]
      (generation-resolver-2 llm-config opts prompt-key (prompt-key prompts)))
    (keys prompts))))

(defn all-keys2
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

(defn- complete*
  [llm-provider-config gen-props context input-data]
  (let [extraction-keys (all-keys2 context input-data)]
    (timbre/info "Resolving for: " extraction-keys)
    (-> (prompt-indexes-2 llm-provider-config gen-props context)
        (resolver-error-wrapper)
        (psm/smart-map input-data)
        (select-keys extraction-keys))))

(defn- fill-converation-slots-2
  [llm-provider-config gen-props context input]
  (mapv
   (fn [{content chat/content :as msg}]
     (assoc msg
            chat/content
            (first (template/fill-slots-2 llm-provider-config gen-props
                                          content
                                          input))))
   context))

(defn- chat* [llm-provider-config gen-props context input]
  (let [updated-context (fill-converation-slots-2 llm-provider-config gen-props context input)
        service-config (get llm-provider-config (llm/service gen-props))
        {:llm/keys [chat-fn]} service-config]
    (->
     (chat-fn (dissoc service-config llm/gen-fn llm/chat-fn)
              (assoc gen-props :messages updated-context))
     bosquet.llm.llm/content
     :completion)))

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
  ([messages] (chat messages {}))
  ([messages inputs] (chat messages inputs {}))
  ([messages inputs opts]
   (let [updated-context (fill-converation-slots messages inputs opts)]
     (complete/chat-completion updated-context opts))))

(defn generate
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

(defn ->completer
  [llm-services]
  (fn [parameters context data]
    (generate llm-services parameters context data)))

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
                                     {bosquet.llm.llm/content
                                      {:completion
                                       {:content "CHAT-LOCAL2"}}})}})))

  (generate
   {llm/service llm/openai}
   "When I was 6 my sister was half my age. Now I’m 70 how old is my sister?")

  (generate
   {:ans {llm/service llm/openai}}
   {:x "When I was {{age}} my sister was half my age. Now I’m 70 how old is my sister? {% gen ans %}"}
   {:age 10})

;; COMPLETION
  (generator
   {:answer {llm/service      llm/openai
             :cache           true
             llm/model-params {:model :gpt-3.5-turbo}}
    :eval   {llm/service :local2}}

   {:question-answer "Question: {{question}}  Answer: {% gen answer %}"
    :self-eval       "{{answer}} Is this a correct answer? {% gen eval%}"}

   {:question "What is the distance from Moon to Io?"})

  ;; CHAT
  (generator
   {llm/service      llm/openai
    llm/model-params {:temperature 0.4
                      :model       :gpt-3.5-turbo}}

   [:system "You are a playwright. Given the play's title and genre write synopsis."
    :user ["Title: {{title}}"
           "Genre: {{genre}}"]
    :user "Playwright: This is a synopsis for the above play:"]

   {:title "Mr. X" :genre "crime"})

  ;; TEMPLATE
  (generator
   {:answer {llm/service llm/openai}}
   "Question: {{question}}  Answer: {% gen answer %}"
   {:question "What is the distance from Moon to Io?"})

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
   {:test   {llm/service :openai}
    :answer {llm/service :openai}}
   {:role            "As a brilliant {{you-are}} answer the following question."
    :question-answer "Question: {{question}}  Answer: {% gen answer %}"
    :self-eval       "{{answer}} Is this a correct answer? {% gen test %}"}
   {:you-are  "astronomer"
    :question "What is the distance from Moon to Io?"})

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
