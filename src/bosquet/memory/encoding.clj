(ns bosquet.memory.encoding)

(def as-is
  "Memory encoder that changes nothing. Observations
  are stored as is."
  :memory.encoding/as-is)

(defn as-is-handler [observation] observation)

(def handlers
  {as-is as-is-handler})

(defn handler [encoder-name]
  (get handlers encoder-name as-is-handler))
