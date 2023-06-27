(ns bosquet.complete
  (:require [bosquet.openai :as openai]
            [bosquet.complete :as complete]))

(defn complete-openai [prompt params]
  (openai/complete prompt (assoc params :impl :openai)))

(defn complete-azure-openai [prompt params]
  (openai/complete prompt (assoc params :impl :azure)))

(defn complete [prompt params]
  (let [impl (:impl params)
        complete-fn 
        (cond 
          (= :azure impl)  complete-azure-openai
          (= :openai impl) complete-openai
          (fn? impl) impl)
        ]
  (complete-fn prompt params))
  )

