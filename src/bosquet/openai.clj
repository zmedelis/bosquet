(ns bosquet.openai
  (:require [wkok.openai-clojure.api :as api]))

(def ada "text-ada-001")

#_:clj-kondo/ignore
(def davinci "text-davinci-003")

#_:clj-kondo/ignore
(def cgpt "gpt-3.5-turbo")


(defn complete-chat
  "Completion using cgpt-3.5. This one is loosing the conversation
  aspect of the API. It will construct basic `system` for the
  conversation and then use `prompt` as the `user` in the chat "
  [prompt opts]
  (-> (assoc opts
        :model cgpt
        :messages [{:role "system" :content "You are a helpful assistant."}
                   {:role "user" :content prompt}])
    (api/create-chat-completion)
    :choices first :message :content))


(defn complete
  "Complete `prompt` if passed in `model` is cGPT the completion will
  be passed to `complete-chat`"
  ([prompt] (complete prompt nil))
  ([prompt {:keys [impl
                   model temperature max-tokens n top-p
                   presence-penalty frequence-penalty]
            :or   {impl              :openai
                   model             ada
                   temperature       0.6
                   max-tokens        250
                   presence-penalty  0.4
                   frequence-penalty 0.2
                   top-p             1
                   n                 1}
            :as opts}]
   (if (= model cgpt)
     (complete-chat prompt opts)
     (->> {:model             model
           :temperature       temperature
           :max_tokens        max-tokens
           :presence_penalty  presence-penalty
           :frequency_penalty frequence-penalty
           :n                 n
           :top_p             top-p
           :prompt            prompt}
         (api/create-completion impl)
       :choices first :text))))

(comment
  (complete "What is your name?" {:max-tokens 10 :model cgpt})
  (complete "1 + 10 =" {:model "testtextdavanci003" :impl :azure}))
