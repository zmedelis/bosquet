(ns bosquet.nlp.similarity
  (:import [org.apache.commons.text.similarity CosineDistance JaccardSimilarity]))

(def cosine-distance (CosineDistance.))

(defn cosine-similarity [s1 s2]
  (.apply cosine-distance s1 s2))


(defn jackard-similarity [s1 s2]
  (.apply (JaccardSimilarity.) s1 s2))

(comment
  (def s1 "I want to explore the potential of integrating LLMs with external knowledge")
  (def s2 "Let's explore how LLMs can integrate with external knowledge")
  (def s3 "I want to explore the city")
  (cosine-similarity s1 s2)
  (cosine-similarity s1 s3)


  (jackard-similarity s1 s2)
  (jackard-similarity s1 s3)
  #__)
