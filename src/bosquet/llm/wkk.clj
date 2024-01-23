(ns bosquet.llm.wkk)

;; # Well Known Keys

(def model :llm/model)

(def service :llm/service)

(def content :llm/content)

(def usage :llm/usage)

(def generation-type :llm/type)

(def openai :openai)

(def cohere :cohere)

(def lmstudio :lmstudio)

(def mistral :mistral)

(def model-params :llm/model-params)
(def gen-fn :llm/gen-fn)
(def chat-fn :llm/chat-fn)
(def complete-fn :llm/complete-fn)
(def var-name :llm/var-name)

(def output-format
  "Type of generation output format: json, xml, text, etc"
  :llm/output-format)

(def context
  "When generating from a prompt map specify which key contains full context for generation."
  :llm/context)

(def cache
  :llm/cache)
