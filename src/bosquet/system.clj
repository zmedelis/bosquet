(ns bosquet.system
  (:import [bosquet.llm.cohere Cohere]
           [bosquet.llm.openai OpenAI])
  (:require
   [bosquet.llm.openai :as openai]
   [integrant.core :as ig]))

(def config
  {:llm/openai {:api-key (System/getenv "OPENAI_API_KEY")}
   :llm/cohere {:api-key (System/getenv "COHERE_API_KEY")}})

(defmethod ig/init-key :llm/openai [_ opts]
  (OpenAI. opts))

(defmethod ig/init-key :llm/cohere [_ opts]
  (System/setProperty "cohere.api.key" (System/getenv "COHERE_API_KEY"))
  (Cohere. opts))

(def system
  (ig/init config))
