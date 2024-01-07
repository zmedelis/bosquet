(ns bosquet.llm.schema)

;; Place to define Malli schemas and conversions between different LLM
;; shapes

(defn model-mapping
  "Check LLM service config if there are any aliases defined.
  If model alias is found return it, if not use the `model` as is.

  Intended for usecases where templates define a certain model name and
  without changes in the template a differently named by other provider
  can be used."
  [{model-map :model-name-mapping} model]
  (get model-map model model))
