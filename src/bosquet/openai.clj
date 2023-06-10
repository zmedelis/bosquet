(ns bosquet.openai
  (:require
   [clojure.string :as string]
   [taoensso.timbre :as timbre]
   [wkok.openai-clojure.api :as api]))

(def ada "text-ada-001")

#_:clj-kondo/ignore
(def davinci "text-davinci-003")

#_:clj-kondo/ignore
(def cgpt "gpt-3.5-turbo")

(defn- get-api-key
  "Read the API key from the environment variable OPENAI_API_KEY or
  form ~/.openai_api_key file"
  []
  (or (System/getenv "OPENAI_API_KEY")
    (string/trim
      (slurp (str (System/getProperty "user.home") "/.openai_api_key")))))

(defn- create-chat
  "Completion using Chat GPT model. This one is loosing the conversation
  aspect of the API. It will construct basic `system` for the
  conversation and then use `prompt` as the `user` in the chat "
  [prompt params opts]
  (-> (api/create-chat-completion
        (assoc params
          :model cgpt
          :messages [{:role "system" :content "You are a helpful assistant."}
                     {:role "user" :content prompt}])
        opts)
    :choices first :message :content))

(defn- create-completion
  "Create completion (not chat) for `prompt` based on model `params` and invocation `opts`"
  [prompt params opts]
  (-> (api/create-completion
        (assoc params :prompt prompt) opts)
    :choices first :text))

(defn complete
  "Complete `prompt` if passed in `model` is cGPT the completion will
  be passed to `complete-chat`"
  ([prompt] (complete prompt nil))
  ([prompt {:keys [impl api-key
                   model temperature max-tokens n top-p
                   presence-penalty frequence-penalty]
            :or   {impl              :openai
                   api-key           (get-api-key)
                   model             ada
                   temperature       0.2
                   max-tokens        250
                   presence-penalty  0.4
                   frequence-penalty 0.2
                   top-p             1
                   n                 1}}]
   (let [params {:impl              impl
                 :model             model
                 :temperature       temperature
                 :max_tokens        max-tokens
                 :presence_penalty  presence-penalty
                 :frequency_penalty frequence-penalty
                 :n                 n
                 :top_p             top-p
                 :prompt            prompt}
         opts   {:api-key api-key}]
     (timbre/infof "Calling OAI with params: '%s'" (dissoc params :prompt))
     (if (= model cgpt)
       (create-chat prompt params opts)
       (create-completion prompt params opts)))))

(comment
  (complete "What is your name?" {:max-tokens 10 :model cgpt})
  (complete "What is your name?" {:max-tokens 10})
  (complete "1 + 10 =" {:model "testtextdavanci003" :impl :azure}))
