(ns bosquet.memory.long-term-memory
  (:require
   [bosquet.memory.memory :as mem]))

(deftype LongTermMemory
         [storage encoder]
  mem/Memory

  (forget
    [_this {:keys [collection-name]}]
    (.delete storage collection-name))

  (remember
    [_this observation {:keys [collection-name]}]
    (let [observations (if (vector? observation) observation [observation])
          embeds       (mapv (fn [text]
                               {:embedding (-> encoder (.encode text) :data first :embedding)
                                :payload   {:text text}})
                             observations)]
      (.add storage collection-name embeds)))

  (free-recall [_this _cueue _params])

  (sequential-recall [_this _params])

  (cue-recall
    [_this cue {:keys [collection-name limit]
                :or   {limit 3}}]
    (.search storage collection-name
             (-> encoder
                 (.encode cue)
                 :data first :embedding)
             limit))

  (volume [_this _opts]))
