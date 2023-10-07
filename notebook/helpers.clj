(ns helpers
  (:require
    [clojure.string :as string]))

(defn kv-cell [k v]
  [:div.flex
   [:div.flex-none.w-24 [:em (str k ":")]]
   [:div.flex-auto v]])

(defn chatml-cell [{:keys [role content]}]
  (kv-cell (string/capitalize (name role)) content))
