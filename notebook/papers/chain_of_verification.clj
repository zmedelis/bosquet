(ns papers.chain-of-verification
  (:require
   [bosquet.llm.generator :as g]
   [bosquet.wkk :as wkk]
   [helpers :as h]))

(def baseline
  {:baseline-instruction
   (h/join
     "Answer the provided question as best as you can. Think carefuly about the question so as not to"
     "include any unverified information. Be consise but also do not omit any important details.")

   :verification-plan-instruction
   (h/join
     "Analyze the QUESTION and ANSWER in above. Think criticaly about the facts provided in the ANSWER."
     "For each mentioned fact, ask a question that would verify that fact."
     "Provide you response using Clojure EDN like this:"
     "[[\"mentioned fact 1\" \"verification question A\"]"
     " [\"mentioned fact 2\" \"verification question B\"]"
     " [\"mentioned fact 3\" \"verification question C\"]]")

   :verification-execution-instruction
   (h/join
     "Answer the flowing questions:"
     "{% for _fact, question in verification-plan %}" "{{forloop.counter}}. {{question}}"
     "{% endfor %}"
     ""
     "Provide very succinct answers using Clojure EDN vector listing anwers in exact same order.")

   :revision-instruction
   (h/join
     "Given a list of QUESTION, CORRECT ANSWER and PROVIDED ANSWER, determine if the provided answer is correct."
     ""
     "QUESTIONS:"
     "{% for _provided-answer, question in verification-plan %}" "QUESTION {{forloop.counter}}. {{question}}"
     "{% endfor %}"
     ""
     "PROVIDED ANSWERS:"
     "{% for provided-answer, _question in verification-plan %}" "PROVIDED ANSWER {{forloop.counter}}. {{provided-answer}}"
     "{% endfor %}"
     ""
     "CORRECT ANSWERS:"
     "{% for correct-answer in verification-execution %}" "CORRECT ANSWER {{forloop.counter}}. {{correct-answer}}"
     "{% endfor %}"
     ""
     "Proivde your answer with 'true' or 'false' only in the same numbered sequence.")

   :verification-plan
   (h/join
     "# BASELINE"
     "{{baseline-instruction}}"
     ""
     "QUESTION: {{question}}"
     "ANSWER: {% gen var-name=baseline-answer %}"
     ""
     "# VERIFICATION PLAN"
     "{{verification-plan-instruction}}"
     ""
     "{% gen var-name=verification-plan %}")

   :verification-execution
   (h/join
     "# VERIFICATION EXECUTION"
     ""
     "{{verification-execution-instruction}}"
     "{% gen var-name=verification-execution %}")

   :revision
   (h/join
     "# REVISION"
     ""
     "{{revision-instruction}}"
     "{% gen var-name=revision-result %}")

   :cov
   (h/join
     "{{verification-plan}}"
     ""
     "{{verification-excecution}}"
     ""
     "{{revision}}"
     ""
     "Given the corrected facts in REVSION reformulate original ANSWER using in a more truthful way."
     ""
     "# REVISED ANSWER"
     ""
     "{% gen var-name=revised-answer %}")})

(def model-params {wkk/model-parameters {:model "gpt-4"}})
(def model-edn-params {wkk/model-parameters {:model "gpt-4"}
                       wkk/output-format    :edn})

(def result
  (g/generate
    baseline
    {:question "What was the primary cause of the Mexican-American war?"}
    {:baseline-answer                    model-params
     :verification-execution-instruction model-edn-params
     :verification-plan                  model-edn-params
     :verification-execution             model-edn-params
     :revision-result                    model-params
     :cov                    model-params
     :revised-answer                     model-params}))
