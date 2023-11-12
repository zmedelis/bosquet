(ns bosquet.eval.qna-generator
  (:require
   [bosquet.llm.generator :as gen]
   [bosquet.utils :as utils]
   [bosquet.wkk :as wkk]))

;;     "Context information is below."
;;     "~~~~~~~~~"
;;     "{{context}}
;;     "~~~~~~~~~"
;;     "Given the context information and not prior knowledge, "
;;     "answer the query."
;;     "Query: {query_str}"
;;     "Answer:


(def prompts
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
   (utils/join-nl
     "CONTEXT INFORMATION is below:"
     "~~~~~~"
     "{{context}}"
     "~~~~~~")
   :qna
   (utils/join-nl
     "{{role}}"
     ""
     "{{question-generation}}"
     "{{format}}"
     "{% gen var-name=questions%}")})

(comment
  (gen/generate
   prompts
   {:question-number 3
    :context
    "Open-Domain Question Answering (ODQA) aims at answering factoid questions without
explicitly providing specific background documents. In a zero-shot setting, this task is more
challenging since no data is available to train customized models like Retriever-Readers.
Recently, Large Language Models (LLMs) like GPT-3 have shown their power in zero-shot ODQA with
direct prompting methods, but these methods are still far from releasing the full powerfulness
of LLMs only in an implicitly invoking way. In this paper, we propose a Self-Prompting
framework to explicitly utilize the massive knowledge stored in the parameters of LLMs and
their strong instruction understanding abilities. Concretely, we prompt LLMs step by step to
generate multiple pseudo QA pairs with background passages and explanations from scratch and
then use those generated elements for in-context learning. Experimental results show our
method surpasses previous SOTA methods significantly on three widely-used ODQA datasets, and
even achieves comparable performance with some Retriever-Reader models fine-tuned on full
training data."}
   {:questions
    {wkk/service          [:llm/openai :provider/openai]
     wkk/output-format    :edn
     wkk/cache            true
     wkk/model-parameters {:temperature 0.2 :max-tokens 500 :model "gpt-3.5-turbo"}}}))
