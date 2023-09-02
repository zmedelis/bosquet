(ns bosquet.llm.llm)

(defn model-mapping [{model-map :model-name-mapping} model]
  (get model-map model))

;; LLM interface defining protocol. It is implemented by the
;; services that provide the LLM calls to OpenAI, or any other
;; supported LLM service.
(defprotocol LLM
  (generate [this prompt props])
  (chat     [this system conversation props]))
