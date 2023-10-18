(ns bosquet.nlp.text2vec
  (:require [clojure.java.io :as io])
  (:import
    [org.deeplearning4j.models.paragraphvectors ParagraphVectors ParagraphVectors$Builder]
    [org.deeplearning4j.models.word2vec VocabWord]
    [org.deeplearning4j.models.word2vec.wordstore.inmemory AbstractCache]
    [org.deeplearning4j.text.documentiterator LabelsSource]
    [org.deeplearning4j.text.sentenceiterator SentenceIterator BasicLineIterator]
    [org.deeplearning4j.text.tokenization.tokenizer.preprocessor CommonPreprocessor]
    [org.deeplearning4j.text.tokenization.tokenizerfactory DefaultTokenizerFactory TokenizerFactory]))

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
      (.similarity v "DOC_9835" "DOC_12492"))))

(comment
  (->vectors "raw_sentences.txt"))
