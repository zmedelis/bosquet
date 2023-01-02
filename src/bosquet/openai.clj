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

(defn get-completion
  ([prompt] (get-completion prompt nil))
  ([prompt {:keys [model temperature max-tokens n
                   presence-penalty frequence-penalty]
            :or   {model             ada
                   temperature       0.7
                   max-tokens        80
                   presence-penalty  0.7
                   frequence-penalty 0.7
                   n                 1}}]
   (let [body {:model             model
               :temperature       temperature
               :max_tokens        max-tokens
               :presence_penalty  presence-penalty
               :frequency_penalty frequence-penalty
               :n                 n
               :prompt            prompt}]
     (-> endpoint
       (http/post (request body (api-key)))
       (completion)))))

(comment
  (get-completion "1 + 10 ="))
