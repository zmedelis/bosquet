(ns bosquet.llm.gen-tree
  (:require
   [clojure.string :as string]))

(defn partition-template
  [template]
  (let [slots (re-seq #"(.*?)(\{\{.*?\}\})" template)]
    (conj
     (mapv first slots)
     ;; attach the remainder of the template text
     (string/replace template (string/join (map first slots)) ""))))

(defn depend-tree
  [key-prefix parts]
  (reduce (fn [m [idx part]]
            (assoc
             m
             (str key-prefix "-" idx)
             (if (zero? idx)
               part
               (format "{{part-%s}}%s" (dec idx) part))))
          {}
          (map-indexed vector parts)))

(comment
  (def in {:tasks "First, I am doing {{A}} followed by {{B}} task."
           :A     "gen-A"
           :B     "gen-B"})

  (depend-tree
   "part"
   (partition-template (:tasks in)))

  (def out
    {:b.tasks/part-1   "First, I am doing {{A}}"
     :b.tasks/part-2   "{{b..tasks/part-1}} followed by {{B}}"
     :b.tasks/part-end "{{b..tasks/part-2}} task"
     :A               "gen-A"
     :B               "gen-B"})
  #__)
