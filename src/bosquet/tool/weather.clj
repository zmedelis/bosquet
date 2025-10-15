(ns bosquet.tool.weather 
  (:require
   [clojure.string :as str]
   [taoensso.timbre :as timbre]))


(defn ^{:desc "Get the current weather in a given location"} get-current-weather
    [^{:type "string" :desc "The city, e.g. Vilnius"} location]
    (timbre/infof "Applying get-current-weather for location %s" location)
    (case (str/lower-case location)
      "vilnius" {:location "Vilnius" :temperature "24" :unit "celcius"}
      "tokyo" {:location "Tokyo" :temperature "30" :unit "celcius"}
      "paris" {:location "Paris" :temperature "27" :unit "celcius"}
      {:location location :temperature "unknown"}))
