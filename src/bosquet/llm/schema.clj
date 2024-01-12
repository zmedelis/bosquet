(ns bosquet.llm.schema)

;; Place to define Malli schemas and conversions between different LLM
;; shapes
;;
;; LLM input and output data transformations.
;; a) Change Bosquet data: prompts, chat messages, usage and other elements
;;    into whatever data shape is used be target LLM service
;; b) Change LLM service responces: generations, usage data, etc into a single
;;    representation used by Bosquet

(defn model-mapping
  "Check LLM service config if there are any aliases defined.
  If model alias is found return it, if not use the `model` as is.

  Intended for usecases where templates define a certain model name and
  without changes in the template a differently named by other provider
  can be used."
  [{model-map :model-name-mapping} model]
  (get model-map model model))

(def usage-out-count
  "Usage map key to indicate how many tokens were used for completion"
  :completion)

(def usage-in-count
  "Usage map key to indicate how many tokens were used for prompt"
  :prompt)

(def usage-total-count
  "Usage map key to indicate how many tokens were used for prompt and completion"
  :total)
