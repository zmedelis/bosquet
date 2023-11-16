(ns papers.llms-as-optimizers
  (:require
    [bosquet.eval.evaluator :as eval]
    [bosquet.eval.qna-generator :as qna]
    [bosquet.llm.generator :as gen]
    [bosquet.read.document :as document]
    [bosquet.template.read :as read]
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
    "{% for i in instruction-score-pairs %}"
    "Instruction (<INS>): {{i.instruction}}"
    "Score: {{i.score}}"
    "{% endfor %}"
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
    "{% for q,a in qna-pairs %}"
    "Question: {{q}}"
    "Answer: {{a}}"
    "{% endfor %}"
    ""
    #_"We run every input in an evaluation dataset through an LLM. If the LLM-generated output doesn't match the expected output, we mark it as wrong (score 0)."
    "We run every input in an evaluation dataset through an LLM. If the LLM-generated output doesn't match the expected output, we mark it as wrong (score 1)."
    "Ideal answer has a score of 5. With range in between indicating various matching levels."
    #_"The final 'score' for an instruction is the average of scores across an evaluation dataset."
    "Write your new instruction (<INS>) that is different from the old ones and has a score as high as possible."
    "Be very concise in your instruction. As the same time try to write more genericaly applicable instruction."
    ""
    "<INS>"
    "{% gen var-name=instruction %}"))


(def mem-opts {:collection-name "llama2-qna-eval"
               :encoder         :embedding/openai
               :storage         :db/qdrant})

(def text (:text (document/parse "data/llama2.pdf")))

(eval/remember-knowledge mem-opts text)


(defn optimization-step-prompt
  [instruction]
  {:instruction     instruction
   :prompt-template (u/join-nl
                      "{{instruction}}"
                      "Context information is below."
                      u/separator
                      "{{context}}"
                      u/separator
                      "Query: {{query}}"
                      "Answer:")
   :generation      (u/join-nl
                      "{{prompt-template}}"
                      "{% gen var-name=answer %}")})

(def step-0-prompt
  (optimization-step-prompt "Given the context information and not prior knowledge, answer the query."))

;; Questions and answers golden dataset. Will be used to eval against and optimize the prompts
(def qna-goldenset
  (qna/load-qna-dataset "data/llama2-eval.edn"))

(def question
  (first qna-goldenset))

(def relevant-memories
  (eval/query mem-opts question))

(def answer-from-context
  (:answer
   (gen/generate
     step-0-prompt
     {:query question
      :context relevant-memories}
     {:score wkk/gpt3.5-turbo-with-cache})))

(def score
  (eval/evaluate-answer
    question
    "The key contributions of the Llama 2 project include the development and release of pretrained and fine-tuned large language models (LLMs) optimized for dialogue use cases. The Llama 2-Chat models outperform existing open-source chat models on most benchmarks, and based on human evaluations for helpfulness and safety, they may be a suitable substitute for closed-source models."
    answer-from-context))


(read/fill-slots
  opro-prompt
  {:instruction-score-pairs [{:instruction (:instruction step-0-prompt) :score score}]
   :prompt-template (:prompt-template step-0-prompt)
   :qna-pairs (take 4 qna-goldenset)})


(gen/generate
  opro-prompt
  {:instruction-score-pairs [{:instruction (:instruction step-0-prompt) :score score}]
   :prompt-template (:prompt-template step-0-prompt)
   :qna-pairs (take 4 qna-goldenset)}
  {:score wkk/gpt3.5-turbo-with-cache})
