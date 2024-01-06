(ns user-guide
  (:require
   [bosquet.llm :as llm]
   [bosquet.llm.generator :refer [generate llm]]
   [nextjournal.clerk :as clerk]))

;; # Bosquet Tutorial
;;
;; This notebook will demonstrate the following things:
;; - system configuration
;; - define prompt *templates*
;; - resolve *dependencies* between prompts
;; - produce AI *completions*.
;;
;; ## Configuration
;;
;; ### config.edn
;;
;; In order to use the library all you need is to specify your LLM service configuration. This is done in `config.edn` file.
;; The file is not part of the repository. See `config.edn.sample` for configuration sample. Rename it to `config.edn` and fill in the values.
;;
;; Your config can be as simple as:

{:llm/openai {:api-key "..."}}

;; `config.edn` is loaded from the root of the project. You can overide this with `BOSQUET_CONFIG` environment variable.
;;
;; ### resources/env.edn
;;
;; `env.edn` defines how configuration is loaded for various Bosquet components: LLMs, mempry, etc.
;;
;; It uses [Aero](https://github.com/juxt/aero) library to declare how different config options are merged together.
;;
;; This is how OpenAI service config is declared:
;; ```clojure
;; {:llm/openai {:api-endpoint #or [#env "OPENAI_API_ENDPOINT"
;;                                  "https://api.openai.com/v1"]
;;               :impl         :openai}}
;; ```
;; `:api-endpoint` will take its value from `OPENAI_API_ENDPOINT` environment variable or use
;; the default value of `https://api.openai.com/v1`. Since config map in `env.edn` merges with
;; a config map declared in `config.edn`, you can override the value in `config.edn` file.
;;
;; Note that *Bosquet* be default is not reading secrets from [environment variables.](https://github.com/juxt/aero?tab=readme-ov-file#hide-passwords-in-local-private-files)
;;
;; ## Generation
;;
;; ### Prompt string completion
;;
;; Simpliest case of using the library is to generate a completion from a prompt.

^{:nextjournal.clerk/auto-expand-results? true}
(generate "When I was 6 my sister was half my age. Now I’m 70 how old is my sister?")

;; Differently from other more complex cases, this returns only completion string and uses
;; default LLM service and model. The dafault of the default is *OpenAI* with *GPT-3.5*.
;;
;; Default LLM is specified in `:llm/default` config key.
;;
;; ### Prompt graph completion
;;
;; A more involved use case is to use linked prompt templates for text generation.
;;

^{:nextjournal.clerk/auto-expand-results? true}
(generate
 llm/default-services
 {:question-answer "Question: {{question}}  Answer:"
  :answer          (llm llm/openai
                        llm/model-params {:temperature 0.8 :max-tokens 120}
                        llm/context :question-answer)
  :self-eval       ["Question: {{question}}"
                    "Answer: {{answer}}"
                    ""
                    "Is this a correct answer?"]
  :test            (llm llm/openai
                        llm/context :self-eval
                        llm/model-params {:temperature 0.2 :max-tokens 120})}
 {:question "What is the distance from Moon to Io?"})

;; This shows how to use `generate` with all the parameters:
;; - `services` is a map of LLM services to use for generation. Default services can be replaced with your own LLM call implementations.
;; - `prompts` is a map of prompt templates. The map key is a variable name that can be used to reference templates from each other.
;; - `data` to be filled in in the prompt slots.
;;
;; ### Chat completion
;;
;; Bosquet also supports chat completion.
^{:nextjournal.clerk/auto-expand-results? true}
(generate
 [:system "You are an amazing writer."
  :user ["Write a synopsis for the play:"
         "Title: {{title}}"
         "Genre: {{genre}}"
         "Synopsis:"]
  :assistant (llm llm/openai
                  llm/model-params {:temperature 0.8 :max-tokens 120}
                  llm/var-name :synopsis)
  :user "Now write a critique of the above synopsis:"
  :assistant (llm llm/openai
                  llm/model-params {:temperature 0.2 :max-tokens 120}
                  llm/var-name     :critique)]
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

(def sentimental
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

(def sentiments (generate
                 sentimental
                 {:text-type "tweets" :tweets tweets}))

;; Generation results in the same order as `tweets`
^{::clerk/visibility {:code :hide}}
(clerk/html [:pre sentiments])
