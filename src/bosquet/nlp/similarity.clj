(ns bosquet.nlp.similarity
  (:import [org.apache.commons.text.similarity
            CosineDistance JaccardDistance JaroWinklerDistance]))

(defn cosine-distance [s1 s2]
  (.apply (CosineDistance.) s1 s2))

(defn jackard-distance [s1 s2]
  (.apply (JaccardDistance.) s1 s2))

(defn jaro-winkler-distance [s1 s2]
  (.apply (JaroWinklerDistance.) s1 s2))

(comment
  (def s1 "I want to explore the potential of integrating LLMs with external knowledge")
  (def s2 "Let's explore how LLMs can integrate with external knowledge")
  (def s3 "I want to explore the city")
  (cosine-distance s1 s2)
  (cosine-distance s1 s3)

  (jackard-distance s1 s2)
  (jackard-distance s1 s3)

  (jaro-winkler-distance s1 s2)
  (jaro-winkler-distance s1 s3)
  (jaro-winkler-distance s2 s3)
  #__)
