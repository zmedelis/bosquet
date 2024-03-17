(ns bosquet.eval.evaluator
  (:require
   [bosquet.env :as env]
   [bosquet.llm.generator :as gen]
   [bosquet.memory.long-term-memory]
   [bosquet.nlp.splitter :as splitter]
   [bosquet.read.document :as document]
   [bosquet.utils :as u]
   [bosquet.wkk :as wkk]
   [taoensso.timbre :as timbre])
  (:import
   [bosquet.memory.long_term_memory LongTermMemory]))

;; Prompts taken from
;; https://github.com/run-llama/llama_index/blob/f065b6c103677b33990cdd3054a7918c0fe793f8/llama_index/evaluation/correctness.py

(def eval-prompt
  {:system ["You are an expert question answering evaluation system."

            "You are given the following information:"
            "- a user query,"
            "- a reference answer, and"
            "- a generated answer."
            ""
            "Your job is to judge the relevance and correctness of the generated answer."
            "Output a single score that represents a holistic evaluation."
            "You must return your response in a line with only the score."
            "Do not return answers in any other format."
            "On a separate line provide your reasoning for the score as well."
            ""
            "Follow these guidelines for scoring:"
            "- Your score has to be between 1 and 5, where 1 is the worst and 5 is the best."
            "- If the generated answer is not relevant to the user query, you should give a score of 1."
            "- If the generated answer is relevant but contains mistakes, you should give a score between 2 and 3."
            "- If the generated answer is relevant and fully correct, you should give a score between 4 and 5."
            ""
            "Example Response:"
            "Score: 4.0"
            "Explanation: The generated answer has the exact same metrics as the reference answer,"
            "but it is not as concise."]
   :eval  ["{{system}}"
           ""
           "## User Query"
           "{{query}}"
           ""
           "## Reference Answer"
           "{{reference-answer}}"
           ""
           "## Generated Answer"
           "{{generated-answer}}"
           ""
           "## Evaluation"
           "{{score}}"]
   :score (gen/llm :gpt-4)})

(defn evaluate-answer
  [question reference-answer generated-answer]
  (try
    (let [resp (gen/generate
                eval-prompt
                {:query question
                 :reference-answer reference-answer
                 :generated-answer generated-answer}
                {:score wkk/gpt4-turbo-with-cache})]
      (->> resp
           :score
           (re-find #"Score: (.*)")
           second
           (Double/parseDouble)))
    (catch Exception e
      (timbre/errorf "Failed to parse evaluation answer - %s" (ex-message e)))))

;; TODO this does not belong here
(defn store-knowledge
  [{collection-name :collection-name :as opts}
   memory knowledge]
  (let [chunks  (splitter/chunk-text
                 {splitter/chunk-size 20 splitter/split-unit splitter/sentence}
                 knowledge)
        _       (timbre/debugf "Got %s cunks to remember" (count chunks))]
    (.forget memory opts)
    ;; FIXME
    (.create nil #_storage collection-name)
    (doseq [chunk chunks]
      (.remember memory chunk opts))))

;; TODO this does not belong here
(defn query
  [opts memory query]
  (->
   (.cue-recall memory query opts)
   first :payload :text))

(comment
  (import 'bosquet.db.qdrant.Qdrant)
  (import 'bosquet.nlp.embeddings.OAIEmbeddings)
  (def opts {:collection-name "llama2-qna-eval"})
  (def memory (LongTermMemory.
               (Qdrant. (:qdrant env/config))
               (OAIEmbeddings. (:openai env/config))))
  (def text (:text (document/parse "data/llama2.pdf")))

  (store-knowledge opts memory text)
  (query opts memory "What are the inputs and outputs to Reward Modeling?")

  (evaluate-answer
   "What are the inputs and outputs to Reward Modeling?"
   "The reward model takes a model response and its corresponding prompt as inputs. It outputs a scalar score to indicate the quality of the model generation."
   "Inputs: response. Outputs: score")

  #__)
