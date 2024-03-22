(ns bosquet.wkk)

;;
;; Well Known Keys to refer to various concepts properties,
;; system components, etc.
;;

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

;;
;; Memory
;;

(def memory-config
  "Key to reference memory in configuration"
  :bosquet.memory/config)

(def recall-parameters
  "Memory parameters to be used when creating and retrieving a memory"
  :bosquet.recall/parameters)

(def memory-system
  "Memory system implementing memory/Memory protocol to be used in gen AI workflow"
  :bosquet.memory/system)

(def recall-function
  "Function to retrieve memory using passed in memory type"
  :bosquet.recall/function)

;;
;; Misc shortcuts
;;
(def oai-service :llm/openai)

(def gpt3.5-turbo-with-cache
  {service          oai-service
   cache            true
   model-parameters {:model :gpt-3.5-turbo}})

(def gpt4-turbo-with-cache
  {service          oai-service
   cache            true
   model-parameters {:model :gpt-4-1106-preview}})
