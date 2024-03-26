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
(def chat-fn :chat-fn)
(def complete-fn :complete-fn)
(def embed-fn :embed-fn)
(def var-name :llm/var-name)

(def output-format
  "Type of generation output format: json, xml, text, etc"
  :llm/output-format)


(def cache
  "LLM call parameter specifying if cached results should be used"
  :llm/cache)


(def fun-impl
  :fun/impl)


(def fun-args
  :fun/args)
