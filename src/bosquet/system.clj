(ns bosquet.system
  (:require [bosquet.llm.cohere :as cohere]
            [bosquet.llm.openai :as oai]
            [aero.core :as aero]
            [integrant.core :as ig])
  (:import [bosquet.llm.cohere Cohere]
           [bosquet.llm.openai OpenAI]))

;; Key to reference sytem in option maps
(def system-key :bosquet/system)

(def config (dissoc (aero/read-config "system.edn") :props))

(defmethod ig/init-key :llm/openai [_ opts]
  (OpenAI. opts))

(defmethod ig/init-key :llm/cohere [_ {:keys [api-key] :as opts}]
  (System/setProperty "cohere.api.key" api-key)
  (Cohere. opts))

(def system
  (ig/init config))


(defn resource [key] (get system key))

;;; Convenience functions to get LLM API instances

(defn openai [] (get system [:llm/openai :provider/openai]))

(defn azure [] (get system [:llm/openai :provider/azure]))

(defn cohere [] (get system :llm/cohere))
