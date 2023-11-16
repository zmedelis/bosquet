(ns papers.llms-as-optimizers
  (:require
    [bosquet.eval.evaluator :as eval]
    [bosquet.read.document :as document]
    [bosquet.utils :as u]))

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


(def opts {:collection-name "llama2-qna-eval"
           :encoder         :embedding/openai
           :storage         :db/qdrant})

(def text (:text (document/parse "data/llama2.pdf")))

(eval/remember-knowledge opts text)


(eval/query opts "What are the inputs and outputs to Reward Modeling?")


(eval/evaluate-answer

  "What are the inputs and outputs to Reward Modeling?"
  "The reward model takes a model response and its corresponding prompt as inputs. It outputs a scalar score to indicate the quality of the model generation."
  "Inputs: response. Outputs: score")
