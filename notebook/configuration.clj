(ns configuration
  (:require
   [bosquet.llm :as llm]
   [bosquet.llm.generator :as g]))

;; ## Bosquet Configuration
;;
;; *Bosquet* configuration allows you to declare:
;; * LLM services
;; * Model parameters
;; * Model aliases
;; * Switch on caching
;; * Memory components
;;
;; ### Available components
;;
;; Bosquet supports and provides default configurations for a set of LLMs and memory components. Those are defined in `resources/env.edn` file. This
;; file is used internaly to handle component loading. It is not necessary to change it if you will be declaring your own LLMs or other resources (see bellow on how to add those).
;;
;; Note that `env.edn` is not declaring secrets. Those are loadind from `config.edn` found in the root of the project. See `config.edn.sample`
;;
;; ### Configuring LLM calls
;;
;; Bosquet allows to separately declare how LLM call is to be made from the declared templates. It varies slightly depending on the
;; generation mode.
;;
;; When generating using a prompt map, a LLM call is defined a node in the map.

(def prompt {:question-answer "Question: {{question}}  Answer:"
             :answer          (g/llm llm/openai llm/context :question-answer)
             :self-eval       ["Question: {{question}}"
                               "Answer: {{answer}}"
                               "Is this a correct answer?"]
             :test            (g/llm llm/openai llm/context :self-eval)})

;; `self-eval` and `test` nodes define LLM calls, both request `openai` to be used as the LLM service.
;; `llm/context` specifies which map key holds a prompt to be used as the LLM context.
;;
;; How the call is to be made to the LLM service is defined in service configuration. It will have `:openai` key where
;; functions to make a call are defined. Bosquet provides default service implementations in - `bosquet.llm/default-services`.
;;
;; For example `:openai` is defined like this:
;;
;; ```clojure
;;
;; {:openai {:api-key      (env/val :llm/openai :openai-api-key)
;;           :api-endpoint (env/val :llm/openai :api-endpoint)
;;           :impl         (env/val :llm/openai :impl)
;;           complete-fn   handle-openai-complete
;;           chat-fn       handle-openai-chat}}
;; ```
;; It defines chat and complete functions. You can folow that implementation to add your own services

;; A call with this configuration:
;;
^{:nextjournal.clerk/auto-expand-results? true}
(g/generate llm/default-services
            prompt
            {:question "What is the distance from Moon to Io?"})

;; Generating with chat mode is similar

^{:nextjournal.clerk/auto-expand-results? true}
(g/generate
 [:system "You are an amazing writer."
  :user ["Write a synopsis for the play:"
         "Title: {{title}}"
         "Genre: {{genre}}"
         "Synopsis:"]
  :assistant (g/llm llm/openai
                    llm/model-params {:temperature 0.8 :max-tokens 120}
                    llm/var-name :synopsis)
  :user "Now write a critique of the above synopsis:"
  :assistant (g/llm llm/openai
                    llm/model-params {:temperature 0.2 :max-tokens 120}
                    llm/var-name :critique)]
 {:title "Mr. X"
  :genre "Sci-Fi"})

;; Importatn to note the difference from map based prompts. There we do not know the name of the generation node.
;; Therefore, the need to declare `llm/var-name` so that Bosquet knows where to assign the generated text and allow
;; it's use further in the conversation.
