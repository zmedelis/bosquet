(ns bosquet.wkk)

;;
;; Well Known Keys to refer to various concepts properties,
;; system components, etc.
;;

(def llm-config
  "Key to reference LLM service components in props when making `generate` calls."
  :bosquet.llm/llm-config)

(def default-llm
  "Key referencing default LLM service in a `system.edn` system config."
  :llm/default-llm)

(def service
  "Key to reference LLM service name in gen call parameters"
  :bosquet.llm/service)

(def model-parameters
  "Key to reference LLM model parameters in gen call parameters"
  :bosquet.llm.model/parameters)

(def output-format
  "Type of generation output format: json, xml, text, etc"
  :bosquet.llm.output/format)

(def cache
  :bosquet.llm/cache)
