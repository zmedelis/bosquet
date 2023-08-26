(ns bosquet.llm.llm)

;; LLM interface defining protocol. It is implemented by the
;; services that provide the LLM calls to OpenAI, or any other
;; supported LLM service.
(defprotocol LLM
  (generate [this prompt])
  (chat     [this system conversation]))
