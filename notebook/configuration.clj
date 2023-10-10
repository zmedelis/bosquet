(ns configuration
  (:require
    [bosquet.llm.generator :as g]
    [bosquet.system :as system]))

;; ## Bosquet Configuration

;; *Bosquet* uses [Integrant](https://github.com/weavejester/integrant) to setup its components. Combining it with [Aero](https://github.com/juxt/aero) brings in some nice configuration
;; options.

;; *Bosquet* configuration allows you to declare:
;; * LLM services
;; * Model parameters
;; * Model aliases
;; * Switch on caching
;; * Memory components

;; System components are listed in the *Integrant* `resources/system.edn` file. Using it together with *Areo* allows us to specify how externally defined properties are used, declaring alternative ways of loading them, and setting the defaults.

;; Properties like API keys, passwords, or other secrets are not stored in the `system.edn` they go into the `config.edn` file. The `config.edn.sample` file shows available fields. It is not required to list all of them in your `config.edn`

;; ### Configuring LLM services

;; LLM service components wrap access to the APIs provided by LLM vendors like *OpenAI*.
;; Currently *Bosquet* supports *OpenAI* and *Cohere* LLM services. A new service can be integrated by implementing the `bosquet.llm.llm/LLM` protocol.

;; Let's take the OpenAI model provided by OpenAI as an example. `system.edn` under `[:llm/openai :provider/openai]`
;; the key defines it like this:
;; ```edn
;; {:api-key      #ref [:config :openai-api-key]
;;  :api-endpoint #or [#env "OPENAI_API_ENDPOINT"
;;                     #ref [:config :openai-api-endpoint]
;;                     "https://api.openai.com/v1"]
;;  :impl         :openai}
;; ```

;; `api-key` is loaded via *Aero* `#ref` construct. It allows loading values from the external configuration file (`config.edn`). Note the `api-endpoint` with `#or` construct, where the sequence of property lookup is defined: start with environment variables, then check the config file, and lastly, use the default value.

;; You would hardly need to access LLM services from your code, but for some low-level tinkering, you can get the LLM Service like this:

(def service (system/get-service [:llm/openai :provider/openai]))
(.service-name service)

;; LLM service declaration accepts the optional `model-name-mapping` parameter. This allows the use of a unified naming scheme for models provided by different vendors. The benefit of that is that you can declare prompt templates and their invocation parameters without having to
;; tie them into specific model names like `gpt4`. Instead, you can use a  generic name like `smart` and map it to the model name in the `model-name-mapping` parameter. With that, you can switch between models without changing your prompt setup.
;; See `:llm/cohere` config for an example of that, where *Cohere* models are mapped to OpenAI
;; model names (subject to change).
;;
;; ### Configuring LLM models
;;
;; Differently from LLM services, LLM models are not components. They are just a map of
;; parameters that are passed to the LLM service when it is invoked. Importantly *Bosquet* allows the definition of different invocation parameters per individual template.
;;
;; For example, given the following `prompt` and `data`

(def prompt
  {:role "As a brilliant {{you-are}} answer the following question."
   :question "What is the distance between Io and Europa?"
   :question-answer "Question: {{question}}  Answer: {% gen var-name=answer %}"
   :self-eval "{{answer}} Is this a correct answer? {% gen var-name=test %}"})

(def data
  {:you-are "astronomer"
   :question "What is the distance from Moon to Io?"})


;; The `parameters` for generation can be defined in a map with configuration for each `gen` call in the template.
;; So the `question-answer` generation is done with the *OpenAI* `gpt-3.5-turbo` model. With the cache enabled.
;;
;; While `self-eval` generation is done with *Cohere* and a low `temperature` setting (note the `gpt-4` model name in use, it gets mapped to Cohere's `command`).

(def parameters
  {:question-answer {:bosquet.llm/service          [:llm/openai :provider/openai]
                     :bosquet.llm/cache            true
                     :bosquet.llm/model-parameters {:temperature 0.4
                                                    :model       "gpt-3.5-turbo"}}
   :self-eval       {:bosquet.llm/service          :llm/cohere
                     :bosquet.llm/model-parameters {:model "gpt-4"
                                                    :temperature 0.0}}})
;;
;; Available configuration parameters are:
;; * `:bosquet.llm/service` - LLM service to use for generation
;; * `:bosquet.llm/cache` - whether to cache the generation result
;; * `:bosquet.llm/model-parameters` - LLM model parameters to use for generation
;;   * all the parameters supported by the LLM service like `temperature`, `max-tokens`, etc. Different services support different parameters and name them differently, *Bosquet* uses the OpenAI naming scheme and normalizes others to it.
;;

;; An example of the generation with given parameters:

^{:nextjournal.clerk/auto-expand-results? true}
(g/generate prompt data parameters)
