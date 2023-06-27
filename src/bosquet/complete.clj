(ns bosquet.complete
  (:require [bosquet.openai :as openai]
            [bosquet.complete :as complete]))


(defn complete [prompt params]
  (let [impl (:impl params)
        complete-fn 
        (cond 
          (= :azure impl)  openai/complete-azure-openai
          (= :openai impl) openai/complete-openai
          (fn? impl) impl)
        ]
  (complete-fn prompt params))
  )

