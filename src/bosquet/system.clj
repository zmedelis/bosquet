(ns bosquet.system
  (:require [bosquet.llm.cohere :as cohere]
            [bosquet.llm.openai :as oai]
            [aero.core :as aero]
            [integrant.core :as ig])
  (:import [bosquet.llm.cohere Cohere]
           [bosquet.llm.openai OpenAI]))

;;
;; Keys to reference sytem components in option maps
;;
(def llm-service-key
  "Key to reference LLM service components in props when making `generate` calls."
  :bosquet/llm-services)

(def default-llm-key
  "Key referencing default LLM service in a `system.edn` system config."
  :llm/default-llm)

(def ^:private config (aero/read-config "system.edn"))

(def ^:private sys-config
  (dissoc config :config default-llm-key))

;;
;; LLM Services
;;

(defmethod ig/init-key :llm/openai [_ opts]
  (OpenAI. opts))

(defmethod ig/init-key :llm/cohere [_ {:keys [api-key] :as opts}]
  (System/setProperty "cohere.api.key" api-key)
  (Cohere. opts))

(def system
  (ig/init sys-config))

;;
;; Convenience functions to get LLM API instances
;;

(defn default-llm []
  (get system (config default-llm-key)))

(defn openai []
  (get system [:llm/openai :provider/openai]))

(defn azure []
  (get system [:llm/openai :provider/azure]))

(defn cohere []
  (get system :llm/cohere))

(defn llm-service
  "Get LLM service by Integrant confg key. If there is none
  configured under that key - get the default one specified under
  `:llm/default` key."
  [key]
  (or (get system key) (default-llm)))
