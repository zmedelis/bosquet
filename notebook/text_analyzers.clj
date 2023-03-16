(ns text-analyzers
  (:require
    [bosquet.prompt-palette :as pp]))

;; ## Text Analyzer prompts

;; Text analysis prompts are defined in resources/prompt-palette/text-analyzers.edn

;; ### Sentiment Analysis
;;
;; #### Single tweet sentiment analysis

(def sentimental (pp/generator :text-analyzer/extract-fact))

(sentimental
  {:text-type "tweet"
   :fact      "sentiment"
   :text      "How did everyone feel about the Climate Change question last night? Exactly."})


;; #### Batch sentiment analysis

(def sentimental-batch (pp/generator :text-analyzer/extract-fact-batch))

(sentimental-batch
  {:text-type "tweets"
   :fact      "sentiment"
   :text
   ["How did everyone feel about the Climate Change question last night? Exactly."
    "Didn't catch the full #GOPdebate last night. Here are some of Scott's best lines in 90 seconds."
    "The biggest disappointment of my life came a year ago."]})
