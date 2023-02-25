(ns bosquet.openai
  (:require [wkok.openai-clojure.api :as api]))

(def ada "text-ada-001")

#_:clj-kondo/ignore
(def davinci "text-davinci-003")

(defn complete
  ([prompt] (complete prompt nil))
  ([prompt {:keys [model temperature max-tokens n top-p
                   presence-penalty frequence-penalty]
            :or   {model             ada
                   temperature       0.6
                   max-tokens        250
                   presence-penalty  0.4
                   frequence-penalty 0.2
                   top-p             1
                   n                 1}}]
   (-> {:model             model
        :temperature       temperature
        :max_tokens        max-tokens
        :presence_penalty  presence-penalty
        :frequency_penalty frequence-penalty
        :n                 n
        :top_p             top-p
        :prompt            prompt}
     api/create-completion
     :choices first :text)))

(comment
  (complete "1 + 10 ="))
