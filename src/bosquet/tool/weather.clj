(ns bosquet.tool.weather 
  (:require
   [clojure.string :as str]
   [taoensso.timbre :as timbre]))


(defn ^{:desc "Get the current weather in a given location"} get-current-weather
    [^{:type "string" :desc "The city, e.g. San Francisco"} location]
    (timbre/infof "Applying get-current-weather for location %s" location)
    (case (str/lower-case location)
      "tokyo" {:location "Tokyo" :temperature "10" :unit "fahrenheit"}
      "san francisco" {:location "San Francisco" :temperature "72" :unit "fahrenheit"}
      "paris" {:location "Paris" :temperature "22" :unit "fahrenheit"}
      {:location location :temperature "unknown"}))
