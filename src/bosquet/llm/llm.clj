(ns bosquet.llm.llm
  (:require [malli.transform :as mt]
            [malli.core :as m]))

#_(def llm-model-parameters
    (m/schema
     [:map
      [:model string?]
      [:max-tokens {:optional true} int?]
      [:temperature {:optional true} float?]
      [:n {:optional true} int?]         ; num_generations (max 5 on cohere)
      [:stream {:optional true} boolean?]
      [:logprobs {:optional true} int?]
      [:echo {:optional true} :boolean]                       ;not in Cohere
      [:stop {:optional true} (or nil string? [:list string?])] ; stop-sequences always array in Cohere
      [:presence-penalty {:optional true} float?]
      [:frequence-penalty {:optional true} float?]
      [:logit-bias {:optional true} float?]]))

(def completion-content ::content)

(def completion-usage ::usage)

(defn model-mapping
  "Check LLM service config if there are any aliases defined.
  If model alias is found return it, if not use the `model` as is.

  Intended for usecases where templates define a certain model name and
  without changes in the template a differently named by other provider
  can be used."
  [{model-map :model-name-mapping} model]
  (get model-map model model))

;; LLM interface defining protocol. It is implemented by the
;; services that provide the LLM calls to OpenAI, or any other
;; supported LLM service.
(defprotocol LLM
  (generate [this prompt props])
  (chat     [this conversation props]))
