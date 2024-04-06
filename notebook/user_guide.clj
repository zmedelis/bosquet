^{:nextjournal.clerk/visibility {:code :fold}}
(ns user-guide
  {:nextjournal.clerk/toc true}
  (:require
   [bosquet.db.cache :as cache]
   [bosquet.env :as env]
   [bosquet.llm.generator :refer [generate llm] :as g]
   [bosquet.llm.oai-shaped-llm :as oai]
   [bosquet.llm.wkk :as k]
   [clojure.string :as s]
   [nextjournal.clerk :as clerk]))

;; # Bosquet Tutorial
;;
;; Topics discussed in this tutorial
;; - system *configuration*
;; - define prompt *templates*
;; - resolve *dependencies* between prompts
;; - produce AI *completions* with different LLMs
;; - defining your own LLM provider
;;
;; ## Configuration
;;
;; *Bosquet* configuration is defined in `resources/env.edn` file. It defines the
;; configuration for supported LLMs and other components. At the bottom, it
;; includes two custom configuration files.
;;
;; - `config.edn` where you can define config for your system (see `config.sample.edn`)
;; - `secrets.edn` is a place where you would put API_KEYS and other secret properties, see `secrets.sample.edn`
;;
;; Access to configuration is managed through `bosquet.env` namespace.
;;
;; Example of *Ollama* configuration:

^{:nextjournal.clerk/auto-expand-results? true}
(:ollama env/config)

;; The *fn* functions: `chat-fn`, `complete-fn`, `embed-fn` define which
;; functions will be called for chat and completion generation, and the one for
;; embedding generation. More on that in *Defining custom LLM calls* section below.
;;
;; `config.edn` and `secrets.edn` are loaded from the root of the project or from *USER_HOME/.bosquet* folder.
;;
;; ```
;; #include #or ["./config.edn"
;;               #join [#env HOME "/.bosquet/config.edn"]]
;; #include #or ["./secrets.edn"
;;               #join [#env HOME "/.bosquet/secrets.edn"]]
;; ```
;; * *Bosquet* uses [Aero](https://github.com/juxt/aero) library to declare how different config options are merged
;; * *Bosquet* by default is not reading secrets from [environment variables.](https://github.com/juxt/aero?tab=readme-ov-file#hide-passwords-in-local-private-files)
;;
;; The following LLM components are defined in Bosquet's `env.edn`:
^{:nextjournal.clerk/visibility {:code :hide}}
(clerk/md (s/join "\n" (map #(str "* " %) (keys env/config))))
;;
;; ## Generation
;; ### String completion
;;
;; The simplest case of using the library is to generate a completion from a prompt.

^{:nextjournal.clerk/auto-expand-results? true}
(clerk/code
 (generate "When I was 6 my sister was half my age. Now I’m 70 how old is my sister?"))

;; Differently from other more complex prompting cases, this returns only the
;; completion string and uses the default LLM service and model. The default LLM
;; service is defined in `config.edn`
;;
;; ```
;; {:default-llm {:temperature 0 :model :mistral-small}}
;; ```
;;
;; ### Tree completion
;;
;; A more involved use case is to use linked prompt templates for text
;; generation. This allows us to:
;;
;; * define *dependencies* between different prompt blocks
;; * create *multiple* generations
;; * *pipe* generation and templating results
;; * get token *usage* counts
;; * provide input data to *fill* in template slots
;;
^{:nextjournal.clerk/auto-expand-results? true}
(generate
 {:question-answer "Question: {{question}}  Answer: {{answer}}"
  :answer          (llm :gpt-3.5-turbo k/model-params {:temperature 0.8 :max-tokens 120})
  :self-eval       ["{{question-answer}}"
                    ""
                    "Is this a correct answer? {{test}}"]
  :test            (llm :mistral-small k/model-params {:temperature 0.2 :max-tokens 120})}
 {:question "What is the distance from Moon to Io?"})

;; Using *tree* generation we can define separate question-answering and answer
;; evaluation prompt blocks.
;;
;; Furthermore, LLM generation is defined as separate
;; nodes in the map and referred to from the templates. The input data (question
;; in this case) is supplied through a separate parameter.
;;
;; The input data (*question* in this case) is supplied through a separate parameter.
;; This allows to define a prompt genereation function

(def quiz (partial generate
                   {:question-answer "Question: {{question}}  Answer: {{answer}}"
                    :answer          (llm :ollama k/model-params {:model :llama2})}))

;; That can be used to process a batch of input data
;;
^{:nextjournal.clerk/auto-expand-results? true}
(mapv quiz [{:question "What is a point?"}
            {:question "What is a space?"}
            {:question "What is an angle?"}])

;;
;; ### Chat completion
;;
;; Constructing prompts as linear chats is also supported. Chats are constructed
;; as a vector of tuples - `[role message]`.
;; LLM node definition is slightly different from the one done in the tree
;; prompt, there it has an additional `:llm/var-name` parameter. It specifies the
;; key in the result map where the generation will be stored.

^{:nextjournal.clerk/auto-expand-results? true}
(generate
 [[:system "You are an amazing writer."]
  [:user ["Write a synopsis for the play:"
          "Title: {{title}}"
          "Genre: {{genre}}"
          "Synopsis:"]]
  [:assistant (llm :gpt-3.5-turbo
                   k/model-params {:temperature 0.8 :max-tokens 120}
                   k/var-name :synopsis)]
  [:user "Now write a critique of the above synopsis:"]
  [:assistant (llm :gpt-3.5-turbo
                   k/model-params {:temperature 0.2 :max-tokens 120}
                   k/var-name     :critique)]]
 {:title "Mr. X"
  :genre "Sci-Fi"})

;; ## Selmer templates
;;
;; [*Selmer*](https://github.com/yogthos/Selmer) provides lots of great templating
;; functionality. An example of some of those features.
;;
;; Lets say we want to get a batch sentiment processor for Tweets.
;;
;; A template for that showing Selmer's `for` loop:

(def sentimental
  {:text     ["Estimate the sentiment of the following batch of {{text-type|default:tweets}}"
              "as positive, negative or neutral:"
              "{% for t in tweets %}"
              "* {{t}}"
              "{% endfor %}"
              "Sentiments:"
              "{{classify}}"]
   :classify (llm :ollama :llm/model-params {:model :llama2})})

;; Tweets to be processed
(def tweets
   ["How did everyone feel about the Climate Change question last night? Exactly."
    "Didn't catch the full #GOPdebate last night. Here are some of Scott's best lines in 90 seconds."
    "The biggest disappointment of my life came a year ago."])

;; The template also has `text-type` filed, but for the *default value* demo purposes it is not
;; supplied. Default value is supplied in the template `{{text-type|default:tweets}}`

(def sentiments (generate sentimental {:tweets tweets}))

;; Generation results in the same order as `tweets`
^{::clerk/visibility {:code :hide}}
(clerk/code (-> sentiments g/completions :text))

;; ## LLM configuration
;;
;; ### Defining LLM call node
;;
;; LLM calls are defined as maps
;; ```edn
;; {:llm/service :ollama
;;  :llm/cache true
;;  :llm/model-params {:temperature 0.3}}
;; ```
;; *Bosquet* has a helper function to make those definitions slightly briefer

(llm :gpt-3.5-turbo k/model-params {:max-tokens 1})

;;Note that llm function can take either the model name or the LLM service name
;;as the first parameter. If a model name is provided, it will consult env.edn
;;to find out who is providing the requested model. When this is not available
;;or there are multiple potential providers then specifying the provider and
;;model is necessary

(llm :ollama k/model-params {:model :mistral})

;; ### Defining custom LLM calls
;;
;; Let us say none of the available LLM configurations are suitable. A custom
;; LLM provider can be supplied to the Bosquet `generate` calls. The
;; `bosquet.llm.generator/generate` in this tutorial was used with its one and
;; two parameter versions, a three params version allows us to pass in custom
;; generators.

(def user-env {:prefixer
               {:chat-fn
                (fn [_config {:keys [prefix messages]}]
                  {k/usage   {:prompt 1 :completion 1 :total 2}
                   k/content {oai/role    oai/assistant
                              oai/content (str prefix (-> messages first :content))}})}})

(generate user-env
          {:prompt "{{text}} {{gen}}"
           :gen (llm :prefixer k/model-params {:prefix "|||"})}
          {:text "A fox jumps over"})

;; Custom environment can be merged with Bosquet defined environment

(generate (merge user-env env/config)
          [[:user "{{text}}"]
           [:assistant (llm :prefixer
                            k/var-name :gen1
                            k/model-params {:prefix "|||"})]
           [:user "Add a suffix"]
           [:assistant (llm :ollama k/var-name :gen2 k/model-params {:model :mistral})]]
          {:text "A fox jumps over"})

;; ### Caching
;;
;; When the variation in the generated result is not needed and we do not need to make an LLM
;; call if there was a call made previously with the same:
;; * prompt text
;; * model parameters
;;
;; *Bosquet* can cache LLM responses if `:llm/cache true` parameter is added.

;; Reset cache from previous entries
(cache/evict-all)

(def g1 (g/generate {:qna    "Question: {{question}}  Answer: {{answer}}"
                     :answer  (llm :ollama
                                   k/cache true
                                   k/model-params {:model :llama2})}
                    {:question "What is the distance from Moon to Io?"}))

;; Second call with exactly the same context will return fast and with exact same response
;; as above

^{:nextjournal.clerk/auto-expand-results? true}
(def g2 (g/generate {:qna    "Question: {{question}}  Answer: {{answer}}"
                     :answer  (llm :ollama
                                   k/cache true
                                   k/model-params {:model :llama2})}
                    {:question "What is the distance from Moon to Io?"}))

;; Once more with different model parameters, and cache lookup misses forcing a fresh call to LLM.

^{:nextjournal.clerk/auto-expand-results? true}
(def g3 (g/generate {:qna    "Question: {{question}}  Answer: {{answer}}"
                     :answer (llm :ollama
                                   k/cache true
                                   k/model-params {:model :llama2
                                                   :temperature 0.9})}
                    {:question "What is the distance from Moon to Io?"}))

;; Generation times for those three calls:
(clerk/html [:ul
             [:li (str "First call (nothing in cache): " (:bosquet/time g1))]
             [:li (str "Second call (cache hit): " (:bosquet/time g2))]
             [:li (str "Last call (nothing in cache): " (:bosquet/time g3))]])

;; This is a very simple implementation, a fullblown LLM caching implementation example:
;; https://github.com/zilliztech/GPTCache
;;
;; ### Output format
;;
;; It is possible to request generation result to be returned in a specified output. This
;; is done through `:llm/output-format` parameter.
;; Supported output formats:
;;
;; * `:json` - convert generated data to JSON
;; * `:edn` - convert generated data to EDN
;; * `:list` - convert generated bullet or numbered lists to a list data structure
;; * `:number` - convert generated data to a number
;; * `:bool` - convert generated 'Yes/NO/true/fAlse' strings to boolean values
;;
;; Note that specifying `:llm/output-format :edn` will not guarantee that result will
;; be EDN. The prompt has to request it as well.
;;
;; **EDN**
(get-in
 (generate
  [[:system ["As a brilliant astronomer, list distances between planets and the Sun"
             "in the Solar System. Provide the answer in EDN map where the key is the"
             "planet name and the value is the string distance in millions of kilometers."]]
   [:user ["Generate only EDN omit any other prose and explanations."]]
   [:assistant (llm :gpt-3.5-turbo
                    k/var-name :distances
                    k/output-format :edn
                    k/model-params {:max-tokens 300})]])
 [g/completions :distances])

;; **BOOL**
(get-in
 (generate
  {:q "Is 2 = 2? Answer with 'yes' or 'no' only!!! {{a}}"
   :a (llm :gpt-3.5-turbo k/output-format :bool)})
 [g/completions :a])


;; **LIST**
(get-in
 (generate
  {:q "Provide a bullet list of 4 colors. {{a}}"
   :a (llm :gpt-3.5-turbo k/output-format :list)})
 [g/completions :a])
