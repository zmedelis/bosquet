(ns bosquet.nlp.text2vec
  (:require [clojure.java.io :as io])
  (:import
    [org.deeplearning4j.models.paragraphvectors ParagraphVectors$Builder]
    [org.deeplearning4j.models.word2vec.wordstore.inmemory AbstractCache]
    [org.deeplearning4j.text.documentiterator LabelsSource]
    [org.deeplearning4j.text.sentenceiterator BasicLineIterator]
    [org.deeplearning4j.text.tokenization.tokenizer.preprocessor CommonPreprocessor]
    [org.deeplearning4j.text.tokenization.tokenizerfactory DefaultTokenizerFactory]))

(defn ->vectors [file-name]
  (let [iterator (BasicLineIterator. (io/file file-name))
        cache (AbstractCache.)
        t (DefaultTokenizerFactory.)
        source (LabelsSource. "DOC_")]
    (.setTokenPreProcessor t (CommonPreprocessor.))
    (let [v (-> (ParagraphVectors$Builder.)
              (.minWordFrequency 1)
              (.iterations 5)
              (.epochs 1)
              (.layerSize 100)
              (.learningRate 0.025)
              (.labelsSource source)
              (.windowSize 5)
              (.iterate iterator)
              (.trainWordVectors false)
              (.vocabCache cache)
              (.tokenizerFactory t)
              (.sampling 0.0)
              (.build))]
      (.fit v)
      v)))

(defn similarity [v doc1 doc2]
  (.similarity v doc1 doc2))

(comment
  (def v (->vectors "raw_sentences.txt"))
  (similarity v "DOC_9835" "DOC_12492")

  (.inferVector v "This is a sentence.")
  )
