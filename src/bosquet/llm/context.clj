(ns bosquet.llm.context)

(defn- ->id
  [idx role]
  (format "%s-%s" idx (name role)))

(defn chatml->graph
  [chat]
  (->> chat
       (partition 2)
       (map-indexed (fn [idx [role content]]
                      (cons idx [role (if (coll? content) content [content])])))
       (reduce (fn [graph [idx role content]]
                 (assoc graph
                        (keyword (->id idx role))
                        (if (zero? idx)
                          content
                          (cons (->id (dec idx) role) content))))
               {})))


(comment
  (def chat
    [:system "You are a playwright. Given the play's title and genre write synopsis."
     :user ["Title: {{title}}"
            "Genre: {{genre}}"]
     :user "Playwright: This is a synopsis for the above play:"
     :asistant "{% gen play %}"
     :user "Review from a Nice City Times play critic of the above synopsis:"
     :assistant "{% gen review %}"])

  (tap> (chatml->graph chat)))
