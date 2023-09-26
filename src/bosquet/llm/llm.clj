(ns bosquet.llm.llm
  (:require
   [bosquet.llm.chat :as chat]))

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

(def content ::content)

(def usage ::usage)

(def generation-type ::type)

(def token-usage
  [:map
   [:prompt pos-int?]
   [:completion pos-int?]
   [:total pos-int?]])

(def generation-type-values [:enum :chat :completion])

(def chat-response
  [:map
   [generation-type generation-type-values]
   [content chat/chat-ml]
   [usage token-usage]])

(def completion-response
  [:map
   [generation-type generation-type-values]
   [content string?]
   [usage token-usage]])

(def generation-response
  [:or chat-response completion-response])

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
