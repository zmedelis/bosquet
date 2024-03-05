(ns bosquet.llm.lmstudio
  (:require
   [bosquet.env :as env]
   [bosquet.llm.oai-shaped-llm :as oai]
   [bosquet.llm.wkk :as wkk]))


(defn chat
  ([params] (chat (wkk/lmstudio env/config) params))
  ([service-cfg params]
   (oai/create-completion service-cfg params)))


(defn complete
  ([params] (complete (wkk/lmstudio env/config) params))
  ([service-cfg {prompt :prompt :as params}]
   (oai/create-completion service-cfg
                          params
                          [{:role :user :content prompt}]
                          nil)))


(comment
  (complete {:prompt "2+2="})
  (chat
   {:messages [{:role :system :content "You are a calculator. You only converse in this format: expression = answer"}
               {:role :user :content "2-2="}]})
  #__)
