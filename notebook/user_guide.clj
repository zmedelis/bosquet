^{:nextjournal.clerk/visibility {:code :fold}}
(ns user-guide
  (:require
   [bosquet.env :as env]
   [bosquet.llm.generator :refer [generate llm]]
   [bosquet.llm.wkk :as wkk]
   [nextjournal.clerk :as clerk]))

;; # Bosquet Tutorial
;;
;; Topcis convered in this tutorial
;; - system *configuration*
;; - define prompt *templates*
;; - resolve *dependencies* between prompts
;; - produce AI *completions* with different LLMs
;; - defining your own LLM provider
;;
;; ## Configuration
;;
;; ### Service configuration
;;
;; *Bosquet* configuration is defined in `resources/env.edn` file. It defines configuration for
;; supported LLMs and other components. At the bottom it includes two custom
;; configuration files.
;; - `config.edn` where you can define config for your own system, see `config.sample.edn`
;; - `secrets.edn` is a place where you would put API_KEYS and other secret properties, see `secrets.sample.edn`
;;
;; Access to configuration is managed through `bosquet.env` namespace.
;;
;; Example of *Ollama* configuration:

(:ollama env/config)

;; The *fn* functions: `chat-fn`, `complete-fn`, `embed-fn` define which functions will be called for
;; chat and completion generation, and the one for embedding generation. More on that in **Defining your own LLM provider**.

;; `config.edn` and `secrets.edn` are loaded from the root of the project or from *USER_HOME/.bosquet* folder.
;; ```
;; #include #or ["./config.edn"
;;               #join [#env HOME "/.bosquet/config.edn"]]
;; #include #or ["./secrets.edn"
;;               #join [#env HOME "/.bosquet/secrets.edn"]]
;; ```
;; * *Bosquet* uses [Aero](https://github.com/juxt/aero) library to declare how different config options are merged together.
;; * *Bosquet* be default is not reading secrets from [environment variables.](https://github.com/juxt/aero?tab=readme-ov-file#hide-passwords-in-local-private-files)
;;
;; ## Generation
;;
;; ### Prompt string completion
;;
;; Simpliest case of using the library is to generate a completion from a prompt.

^{:nextjournal.clerk/auto-expand-results? true}
(clerk/code
 (generate "When I was 6 my sister was half my age. Now I’m 70 how old is my sister?"))

;; Differently from other more complex cases, this returns only completion string and uses
;; default LLM service and model. The dafault of the default is *OpenAI* with *GPT-3.5*.
;;
;; Default LLM is specified in `:llm/default` config key.
;;
;; ### Prompt graph completion
;;
;; A more involved use case is to use linked prompt templates for text generation.
;;

;; ^{:nextjournal.clerk/auto-expand-results? true}
#_(generate
 llm/default-services
 {:question-answer "Question: {{question}}  Answer: {{answer}}"
  :answer          (llm :openai
                        wkk/model-params {:temperature 0.8 :max-tokens 120})
  :self-eval       ["Question: {{question}}"
                    "Answer: {{answer}}"
                    ""
                    "Is this a correct answer? {{test}}"]
  :test            (llm :openai
                        wkk/model-params {:temperature 0.2 :max-tokens 120})}
 {:question "What is the distance from Moon to Io?"})

;; This shows how to use `generate` with all the parameters:
;; - `services` is a map of LLM services to use for generation. Default services can be replaced with your own LLM call implementations.
;; - `prompts` is a map of prompt templates. The map key is a variable name that can be used to reference templates from each other.
;; - `data` to be filled in in the prompt slots.
;;
;; ### Chat completion
;;
;; Bosquet also supports chat completion.
;; ^{:nextjournal.clerk/auto-expand-results? true}
#_(generate
 [:system "You are an amazing writer."
  :user ["Write a synopsis for the play:"
         "Title: {{title}}"
         "Genre: {{genre}}"
         "Synopsis:"]
  :assistant (llm :openai
                  wkk/model-params {:temperature 0.8 :max-tokens 120}
                  wkk/var-name :synopsis)
  :user "Now write a critique of the above synopsis:"
  :assistant (llm :openai
                  wkk/model-params {:temperature 0.2 :max-tokens 120}
                  wkk/var-name     :critique)]
 {:title "Mr. X"
  :genre "Sci-Fi"})

;;
;; ### Selmer templating language
;;
;; [*Selmer*](https://github.com/yogthos/Selmer) provides lots of great templating
;; functionality. An example of some of those features.
;;
;; #### Tweet sentiment batch processing
;;
;; Lets say we want to get a batch sentiment processor for Tweets.
;;
;; A template for that:

#_(def sentimental
  "Estimate the sentiment of the following batch of {{text-type|default:text}} as positive, negative or neutral:
{% for t in tweets %}
* {{t}}
{% endfor %}

Sentiments:")

;; First, *Selmer* provides [for tag](https://github.com/yogthos/Selmer#for)
;; to process collections of data.
;;
;; Then, `{{text-type|default:text}}` shows how defaults can be used. In this case
;; if `text-type` is not specified `"text"` will be used.

;; Tweets to be processed
(def tweets
   ["How did everyone feel about the Climate Change question last night? Exactly."
    "Didn't catch the full #GOPdebate last night. Here are some of Scott's best lines in 90 seconds."
    "The biggest disappointment of my life came a year ago."])

#_(def sentiments (generate
                 sentimental
                 {:text-type "tweets" :tweets tweets}))

;; Generation results in the same order as `tweets`
;; ^{::clerk/visibility {:code :hide}}
;; (clerk/html [:pre sentiments])
