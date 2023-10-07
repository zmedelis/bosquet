(ns helpers
  (:require
    [clojure.string :as string]))

(defn kv-cell [k v]
  [:div.pb-2
   [:div [:em (str k ":")]]
   [:div v]])

(defn chatml-cell [{:keys [role content]}]
  (kv-cell (string/capitalize (name role)) content))
