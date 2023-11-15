(ns bosquet.eval.evaluator
  (:require
   [bosquet.nlp.splitter :as splitter]
   [bosquet.read.document :as document]
   [bosquet.system :as sys]
   [taoensso.timbre :as timbre]))

(defn qna-correctness [opts eval-service question answer]
  (let [memory (sys/get-memory :memory/long-term-embeddings)]))

(defn remember-knowledge
  [{:keys [memory] :as opts} knowledge]
  (let [memory (sys/get-memory memory)
        chunks (splitter/text-chunker
                {:chunk-size 80 :splitter splitter/sentence-splitter}
                knowledge)
        _      (timbre/debugf "Got %s cunks to remember" (count chunks))]
    (.forget memory opts)
    (doseq [chunk chunks]
      (.remember memory chunk opts))))

(comment
  (def opts {:collection-name "llama2-qna-eval"
             :memory          :memory/long-term-embeddings})

  (def text (:text (document/parse "data/llama2.pdf")))

  (remember-knowledge opts text))
