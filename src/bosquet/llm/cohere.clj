(ns bosquet.llm.cohere
  (:require [bosquet.llm.llm :as llm]
            [cohere.client :as client]
            [taoensso.timbre :as timbre]))

(defn- props->cohere
  "Convert general LLM model properties to Cohere specific ones."
  [{:keys [n stop] :as props}]
  (-> props
      (assoc :num-generations n) (dissoc :n)
      (assoc :stop-sequences stop) (dissoc :stop)))

(defn complete
  ([prompt opts]
   (-> (client/generate (assoc opts :prompt prompt))
       :generations
       first
       :text))
  ([prompt]
   (complete prompt {})))

(deftype Cohere
         [config]
  llm/LLM
  (generate [_this prompt props]
    (let [props (props->cohere props)]
      (timbre/infof "Calling Cohere with:")
      (timbre/infof "\tParms: '%s'" (dissoc props :prompt))
      (timbre/infof "\tConfig: '%s'" (dissoc config :api-key))
      (complete prompt (merge config
                              (assoc
                               props
                               :model (llm/model-mapping config (keyword (:model props))))))))
  (chat     [_this _conversation _props]))

(comment
  (.generate
   (bosquet.system/cohere)
   "A party is about to begin."
   {:model "command"
    :n 2
    :stop-sequences ["\n"]
    :temperature 0.2}))
