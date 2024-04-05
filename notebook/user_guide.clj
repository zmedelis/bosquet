^{:nextjournal.clerk/visibility {:code :fold}}
(ns user-guide
  {:nextjournal.clerk/toc true}
  (:require
   [bosquet.db.cache :as cache]
   [bosquet.env :as env]
   [bosquet.llm.generator :refer [generate llm] :as g]
   [bosquet.llm.wkk :as k]
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
;; embedding generation. More on that in **Defining your own LLM provider**.
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

;; ## Caching
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
