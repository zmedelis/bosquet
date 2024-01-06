(ns bosquet.llm
  (:require
   [bosquet.env :as env]
   [bosquet.llm.cohere :as cohere]
   [bosquet.llm.openai :as openai]))

(def openai :llm/openai)

(def cohere :llm/cohere)

(def lm-studio :llm/lm-studio)

(def service :llm/service)
(def model-params :llm/model-params)
(def gen-fn :llm/gen-fn)
(def chat-fn :llm/chat-fn)
(def complete-fn :llm/complete-fn)
(def var-name :llm/var-name)

(def output-format
  "Type of generation output format: json, xml, text, etc"
  :llm/output-format)

(def context
  "When generating from a prompt map specify which key contains full context for generation."
  :llm/context)

(defn handle-openai-chat [service-config params]
  (openai/chat-completion params service-config))

(defn handle-openai-complete [service-config params]
  (openai/chat-completion params service-config))

(defn handle-cohere-chat [service-config params])

(defn handle-cohere-complete [service-config params]
  (cohere/complete params service-config))

(defn- handle-lm-studio-chat [arg1])

(def default-services
  {openai (merge (env/val openai)
                 {complete-fn handle-openai-complete
                  chat-fn     handle-openai-chat})
   cohere (merge (env/val cohere)
                 {complete-fn handle-cohere-complete
                  chat-fn     handle-cohere-chat})
   :local {complete-fn (fn [_system options] {:eval (str "TODO-" (:gen options) "-COMPLETE")})
           chat-fn     (fn [_system options] {:eval (str "TODO-" (:gen options) "-CHAT")})}})
