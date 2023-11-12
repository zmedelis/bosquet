(ns bosquet.eval.qna-generator
  (:require
   [bosquet.llm.openai-tokens :as otok]
   [bosquet.llm.generator :as gen]
   [bosquet.nlp.splitter :as splitter]
   [bosquet.utils :as utils]
   [bosquet.wkk :as wkk]
   [taoensso.timbre :as timbre]))

;; Some of the details are borrowed from
;; https://github.com/run-llama/llama_index/blob/29ef306ae0536de44840ca5acfdf93d84b9a560c/llama_index/evaluation/dataset_generation.py

(def context-prompt-block
  (utils/join-nl
   "CONTEXT INFORMATION is below:"
   "~~~~~~"
   "{{context}}"
   "~~~~~~"))

(def question-building-prompts
  {:role
   (utils/join-nl
    "You are an excelent Teacher who understands subject material perfectly."
    "Your ability to analyze text is astonishing. Based on that your task is to setup"
    "{{question-number}} questions for the upcoming student examination."
    "The questions should be diverse and cover interesting and important topics and facts across the document."
    "Restrict questions to the CONTEXT INFORMATION provided.")

   :format
   "Provide questions as string Clojure vector."

   :question-generation
   context-prompt-block

   :qna
   (utils/join-nl
    "{{role}}"
    ""
    "{{question-generation}}"
    "{{format}}"
    "{% gen var-name=questions%}")})

(def answering-prompt
  (utils/join-nl
   context-prompt-block
   "Given the context information and not prior knowledge, answer the following QUERIES."
   "QUERIES"
   "{% for q in queroes %}"
   "{{forloop.counter}}. {{q}}{% endfor %}"
   "Answer queries in exact same order as they are listed"
   "Provide ANSWERS as string Clojure vector."
   "ANSWERS: {% gen var-name=answers %}"))

(defn generate-qna-dataset
  [document]
  (let [chunks       (splitter/text-chunker
                      {:chunk-size 50 :splitter splitter/sentence-splitter}
                      document)
        q-gen-params {:questions
                      {wkk/service          [:llm/openai :provider/openai]
                       wkk/output-format    :edn
                       wkk/cache            true
                       wkk/model-parameters {:temperature 0.2 :max-tokens 500 :model "gpt-3.5-turbo"}}}
        a-gen-params {:answers
                      {wkk/service          [:llm/openai :provider/openai]
                       wkk/output-format    :edn
                       wkk/cache            true
                       wkk/model-parameters {:temperature 0.0 :max-tokens 1500 :model "gpt-3.5-turbo"}}}]
    (map-indexed
     (fn [idx chunk]
       (timbre/debugf "QnA for chunk #%s. Toekn count -  %s" idx (otok/token-count chunk :gpt-4))
       (let [questions (:questions (gen/generate
                                    question-building-prompts
                                    {:question-number 5
                                     :context         chunk}
                                    q-gen-params))
             answers   (:answers (gen/generate
                                  answering-prompt
                                  {:queries questions :context chunk}
                                  a-gen-params))]
         {:questions questions
          :answers   answers
          :context   chunk}))
     (take 2 chunks))))

(comment
  (def text (:text (bosquet.read.document/parse "data/llama2.pdf")))

  (def qna (generate-qna-dataset text))
  (tap> qna)

  (gen/generate
   question-building-prompts
   {:question-number 3
    :context (subs text 0 20000)}
   {:questions
    {wkk/service          [:llm/openai :provider/openai]
     wkk/output-format    :edn
     wkk/cache            true
     wkk/model-parameters {:temperature 0.2 :max-tokens 500 :model "gpt-3.5-turbo"}}}))
