(ns bosquet.memory.long-term-memory
  (:require
   [bosquet.memory.encoding :as enc]
   [bosquet.memory.memory :as mem]))

(deftype LongTermMemory
         [opts]
  mem/Memory
  (forget [_this {:keys [collection-name]}]
    (.delete (:storage opts) collection-name))

  (remember [_this observation {:keys [collection-name]}]
    (let [observations (if (vector? observation) observation [observation])
          encoding-handler  (enc/handler (:encoder opts))
          embeds (mapv (fn [text] (encoding-handler text opts))
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
