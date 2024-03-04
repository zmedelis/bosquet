(ns bosquet.llm.oai-shaped-llm
  (:require
   [bosquet.llm.wkk :as wkk]))

(defn with-default
  "If no model is given in `params` add the default"
  [{:keys [model] :as params} default-model]
  (if (or model (nil? default-model))
    params
    (assoc params :model default-model)))

(defn prep-params
  "Shape `params` into the LLM API service required structure.
  Remove or move `Bosquet` parameters.

  If `params` has no `model` specified `default-model` will be used."
  ([params] (prep-params params nil))
  ([params default-model]
   (-> params
       (with-default default-model)
       (dissoc wkk/model-params)
       (merge (wkk/model-params params)))))
