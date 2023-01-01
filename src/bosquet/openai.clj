(ns bosquet.openai
  (:require [clj-http.client :as http]
            [jsonista.core :as j]))

(defn- api-key [] (System/getenv "OPENAI_API_KEY"))

(def ^:private default-generation-parameters
  {:model             "text-ada-001"
   :temperature       0.8
   :max_tokens        100
   :presence_penalty  0.7
   :frequency_penalty 0.7
   :n                 1})

(def endpoint "https://api.openai.com/v1/completions")

(defn get-completion [prompt]
  (let [body (-> default-generation-parameters
               (assoc :prompt prompt)
               (j/write-value-as-string))]
    (-> endpoint
      (http/post
        {:accept       :json
         :content-type :json
         :headers      {"Authorization" (str "Bearer " (api-key))}
         :body         body})
      :body
      (j/read-value j/keyword-keys-object-mapper)
      :choices
      first
      :text)))
