(ns papers.llms-as-optimizers
  (:require
    [bosquet.eval.evaluator :as eval]
    [bosquet.llm.generator :as gen]
    [bosquet.read.document :as document]
    [bosquet.utils :as u]
    [bosquet.wkk :as wkk]))

;; https://arxiv.org/pdf/2309.03409.pdf
;;
;; Optimization is ubiquitous. While derivative-based algorithms have been powerful tools for
;; various problems, the absence of gradient imposes challenges on many real-world applications.
;; In this work, we propose Optimization by PROmpting (OPRO), a simple and effective approach
;; to leverage large language models (LLMs) as optimizers, where the optimization task is
;; described in natural language. In each optimization step, the LLM generates new solutions from
;; the prompt that contains previously generated solutions with their values, then the new
;; solutions are evaluated and added to the prompt for the next optimization step.

(def opro-prompt
  (u/join-nl
    "Your task is to generate the instruction <INS>. Below are some previous instructions with their scores."
    "The score ranges from 1 to 5."
    ""
    "{{instruction-score-pairs}}"
    ""
    "Below we show the task. The <INS> tag is prepended to the below prompt template, e.g. as follows:"
    ""
    "```"
    "<INS>"
    "{{prompt-template}}"
    "```"
    ""
    "The prompt template contains template variables. Given an input set of template variables, the formatted prompt is then given to an LLM to get an output."
    ""
    "Some examples of template variable inputs and expected outputs are given below to illustrate the task. **NOTE**: These do NOT represent the"
    "entire evaluation dataset."
    ""
    "{{qna-pairs}}"
    ""
    "We run every input in an evaluation dataset through an LLM. If the LLM-generated output doesn't match the expected output, we mark it as wrong (score 0)."
    "A correct answer has a score of 1. The final 'score' for an instruction is the average of scores across an evaluation dataset."
    "Write your new instruction (<INS>) that is different from the old ones and has a score as high as possible."
    ""
    "Instruction (<INS>):"))


(def mem-opts {:collection-name "llama2-qna-eval"
               :encoder         :embedding/openai
               :storage         :db/qdrant})

(def text (:text (document/parse "data/llama2.pdf")))

(eval/remember-knowledge mem-opts text)

(def step-0-prompt
  (u/join-nl
    "Context information is below. Given the context information and not prior knowledge, answer the query."
    u/separator
    "{{context}}"
    u/separator
    "Query: {{query}}"
    "Answer:"))

(def question
  "What are the key contributions of the Llama 2 project, and how do the Llama 2-Chat models compare to existing open-source and closed-source chat models based on human evaluations for helpfulness and safety?")

(def relevant-memories
  (eval/query mem-opts question))


(def answer-from-context
  (:gen
   (gen/generate
     step-0-prompt
     {:query question
      :context relevant-memories}
     {:score wkk/gpt3.5-turbo-with-cache})))


(eval/evaluate-answer
  question
  "The key contributions of the Llama 2 project include the development and release of pretrained and fine-tuned large language models (LLMs) optimized for dialogue use cases. The Llama 2-Chat models outperform existing open-source chat models on most benchmarks, and based on human evaluations for helpfulness and safety, they may be a suitable substitute for closed-source models."
  answer-from-context)
