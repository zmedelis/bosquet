(ns helpers
  (:require
   [clojure.string :as string]
   [nextjournal.clerk :as clerk]))

(defn wrap-in-el
  "Wrap collection of HTML elements in another HTML el.

  For example `el = :ul` and `coll = [[:li 1] [:li 2]]
  =>
  `[:ul [:li 1] [:li 2]]`"
  [el coll]
  (vec (cons el coll)))

(defn kv-cell [k v]
  [:div.pb-2
   [:div [:em (str k ":")]]
   [:div v]])

(defn chatml-cell [{:keys [role content]}]
  (kv-cell (string/capitalize (name role)) content))

(defn join [& lines]
  (apply str (interpose "\n" lines)))

(defn text-div [text]
  (clerk/html [:div text]))

(defn text-list [coll]
  (when (seq coll)
    (clerk/html
      (wrap-in-el
        :ul.list-disc
        (->> coll
          (remove string/blank?)
          (mapv #(vector :li %)))))))
