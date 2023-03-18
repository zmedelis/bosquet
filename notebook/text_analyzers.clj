(ns text-analyzers
  (:require
    [bosquet.converter :as converter]
    [bosquet.prompt-palette :as pp]
    [nextjournal.clerk :as clerk]))

;; ## Text Analyzer prompts

;; Text analysis prompts are defined in resources/prompt-palette/text-analysis

;; ### Sentiment Analysis

(def sentimental (pp/generator :classification/classify))

;; Tweet sentiment label

(def tweets
  ["How did everyone feel about the Climate Change question last night? Exactly."
   "Didn't catch the full #GOPdebate last night. Here are some of Scott's best lines in 90 seconds."
   "The biggest disappointment of my life came a year ago."])

{:nextjournal.clerk/visibility {:code :show :result :show}}
(def sentiment-result
  (sentimental {:text-type "tweets"
                :classes   ["positive" "negative" "neutral"]
                :fact      "sentiment"
                :texts     tweets}))

{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def sentiment-labels (-> sentiment-result :completion converter/numbered-items->list vec))

;; Sentiment anlaysis results

{:nextjournal.clerk/visibility {:code :hide :result :show}}
(clerk/table
 {"Tweets"    tweets
  "Sentiment" sentiment-labels})

;; ### Text summarization

#_(def summarizer (pp/generator :text-analyzer/summarize-to-sentence))

#_(def summary
 (summarizer
   {:text-type "paragraph"
    :text "OpenAI released its hotly-anticipated GPT-4 on Tuesday,
providing a 98-page 'technical report' on the latest iteration of its large
language model (LLM). But despite the lengthy documentation and the company's
not-for-profit roots, OpenAI has revealed extremely little information about how
its latest AI actually works — which has experts worried, Venture Beat reports."}))

#_(clerk/html [:pre (:completion summary)])
