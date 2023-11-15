(ns bosquet.eval.evaluator
  (:import
   [bosquet.memory.long_term_memory LongTermMemory])
  (:require
   [bosquet.nlp.splitter :as splitter]
   [bosquet.read.document :as document]
   [bosquet.system :as sys]
   [taoensso.timbre :as timbre]))

(defn qna-correctness [opts eval-service question answer]
  (let [memory (sys/get-memory :memory/long-term-embeddings)]))

(defn remember-knowledge
  [{:keys [storage encoder collection-name] :as opts} knowledge]
  (let [storage (sys/get-service storage)
        memory  (LongTermMemory.
                 storage
                 (sys/get-service encoder))
        chunks  (splitter/text-chunker
                 {:chunk-size 80 :splitter splitter/sentence-splitter}
                 knowledge)
        _       (timbre/debugf "Got %s cunks to remember" (count chunks))]
    (.forget memory opts)
    (.create storage collection-name)
    (doseq [chunk chunks]
      (.remember memory {:text chunk} opts))))

(comment
  (def opts {:collection-name "llama2-qna-eval"
             :encoder :embedding/openai
             :storage :db/qdrant})
  (def text (:text (document/parse "data/llama2.pdf")))

  (remember-knowledge opts text))
