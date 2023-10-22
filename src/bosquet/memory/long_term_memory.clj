(ns bosquet.memory.long-term-memory
  (:require
    [bosquet.memory.memory :as mem]))

(deftype LongTermMemory
         [db encoder]
  mem/Memory
  (forget [_this {:keys [collection-name]}]
    (.delete db collection-name))

  (remember [_this observation {:keys [collection-name]}]
    (let [observations (if (vector? observation) observation [observation])
          embeds (mapv (fn [text]
                         {:data {:text text}
                          :embedding
                          (-> encoder
                            (.create text)
                            :data first :embedding)})
                   observations)]
      (.add db collection-name embeds)))

  (free-recall [_this _cueue _params])

  (sequential-recall [_this _params])

  (cue-recall [_this cue {:keys [collection-name limit]}]
    (.search db collection-name
             (-> encoder
               (.create cue)
               :data first :embedding)
             limit))

  (volume [_this _opts]))
