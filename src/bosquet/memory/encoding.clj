(ns bosquet.memory.encoding)

(def as-is
  "Memory encoder that changes nothing. Observations
  are stored as is."
  :memory.encoding/as-is)

(def as-embeddings
  "Memory encoder that changes nothing. Observations
  are stored as is."
  :memory.encoding/as-embeddings)

(defn as-is-handler [observation _opts] observation)

(defn embeddings-handler [observation {embedder :embedding}]
  {:data      {:text observation}
   :embedding (-> embedder (.create observation) :data first :embedding)})

(def handlers
  {as-is         as-is-handler
   as-embeddings embeddings-handler})

(defn handler
  [encoder-name]
  (get handlers encoder-name as-is-handler))
