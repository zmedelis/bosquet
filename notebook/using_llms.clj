(ns using-llms
  (:require
   [bosquet.llm.generator :as g]
   [bosquet.wkk :as wkk]))

;; # TODO needs updating

;; ## Using LLMs
;;
;; At the moment Bosquet supports the following:
;; - OpenAI both hosted at OpenAI and Azure
;; - Cohere
;; - Local LLMs hosted via LM Studio

;; The same setup can be used for all LLMs. The only difference is the service name.

;; ### Setup
;; First, we define a prompt. It is a simple example for more complex ones see other
;; documentation entries.


(def prompt
  {:role            "As a brilliant {{you-are}} answer the following question."
   :question-answer "{{role}} Question: {{question}}  Answer: {% gen var-name=answer %}"
   :self-eval       "{{answer}} Is this a correct answer? {% gen var-name=test %}"})

;; We also define some parameters for the LLMs. There are more parameters available.
;; See the configuration documentation for more details.

(def params
  {:max-tokens  100
   :temperature 0.0})

;; Last bit is the data to fill in template slots.

(def data
  {:you-are  "astronomer"
   :question "What is the distance from Moon to Io?"})

;; All set. Now we can generate the text. Note that LLMs are generated per `gen` tag.
;; This means that different params or different LLMs can be used for different generation
;; tags within the same process.

;; ### OpenAI

;; ^{:nextjournal.clerk/auto-expand-results? true}
;; (g/generate
;;  prompt data
;;  {:answer {wkk/service          :llm/openai
;;            wkk/model-parameters params}
;;   :test   {wkk/service          :llm/openai
;;            wkk/model-parameters params}})


;; ### Local LLMs via LM Studio
;;
;; This will only work if you have LM Studio running locally. See there for more details:
;; https://lmstudio.ai

;; ^{:nextjournal.clerk/auto-expand-results? true}
;; (g/generate
;;  prompt data
;;  {:answer {wkk/service          :llm/lm-studio
;;            wkk/model-parameters params}
;;   :test   {wkk/service          :llm/lm-studio
;;            wkk/model-parameters params}})

;; ### Cohere
;;
;; Note that `model-parameters` can be omitted. Defaults will be used.
;;

;; ^{:nextjournal.clerk/auto-expand-results? true}
;; (g/generate
;;  prompt data
;;  {:answer {wkk/service :llm/cohere}
;;   :test   {wkk/service :llm/cohere}})

;; ### Mixing LLMs
;;
;; Lastly, lets use different LLMs for different generation tags.

;; ^{:nextjournal.clerk/auto-expand-results? true}
;; (g/generate
;;  prompt data
;;  {:answer {wkk/service :llm/cohere}
;;   :test   {wkk/service :llm/lm-studio}})
