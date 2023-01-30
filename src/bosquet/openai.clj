(ns bosquet.openai
  (:require [clj-http.client :as http]
            [jsonista.core :as j]))

(defn- api-key [] (System/getenv "OPENAI_API_KEY"))

(def ada "text-ada-001")

#_:clj-kondo/ignore
(def davinci "text-davinci-003")

(def endpoint "https://api.openai.com/v1/completions")

(defn- request
  "Construct request object to be sent to OpenAI API
  - `body` to hold OpenAI model generation parameters and the prompt
  - `key` to pass in authentication key"
  [body key]
  {:accept       :json
   :content-type :json
   :headers      {"Authorization" (str "Bearer " key)}
   :body         (j/write-value-as-string body)})

(defn- completion
  "Extract cmpletion text from OpenAI `response`"
  [response]
  (-> response
    :body
    (j/read-value j/keyword-keys-object-mapper)
    :choices
    first
    :text))

(defn complete
  ([prompt] (complete prompt nil))
  ([prompt {:keys [model temperature max-tokens n top-p
                   presence-penalty frequence-penalty]
            :or   {model             ada
                   temperature       0.6
                   max-tokens        80
                   presence-penalty  0.4
                   frequence-penalty 0.2
                   top-p             1
                   n                 1}}]
   (let [body {:model             model
               :temperature       temperature
               :max_tokens        max-tokens
               :presence_penalty  presence-penalty
               :frequency_penalty frequence-penalty
               :n                 n
               :top_p             top-p
               :prompt            prompt}]
     (-> endpoint
       (http/post (request body (api-key)))
       (completion)))))

(comment
  (complete "1 + 10 ="))
