(ns bosquet.memory.long-term-memory
  (:require
   [bosquet.memory.memory :as mem]))

(deftype LongTermMemory
    [opts]
    mem/Memory
    (forget [_this {:keys [collection-name]}]
      (.delete (:storage opts) collection-name))

    (remember [_this observation {:keys [collection-name]}]
      (let [observations (if (vector? observation) observation [observation])
            encoder      (:encoder opts)
            _ (prn "ENCODER " encoder)
            embeds       (mapv (fn [{:keys [text payload]}]
                                 (assoc
                                   (.encode encoder text)
                                   :payload payload))
                           observations)]
        (.add (:storage opts) collection-name embeds)))

    (free-recall [_this _cueue _params])

    (sequential-recall [_this _params])

    (cue-recall [_this cue {:keys [collection-name limit]}]
      (.search (:storage opts) collection-name
        (-> (:endoder opts)
          (.create cue)
          :data first :embedding)
        limit))

    (volume [_this _opts]))
