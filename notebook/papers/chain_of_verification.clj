(ns papers.chain-of-verification
  (:require
   [bosquet.llm.generator :as g]
   [bosquet.wkk :as wkk]
   [helpers :as h]))

(def model-params {wkk/model-parameters {:model "gpt-4"}})
(def model-edn-params (merge model-params {wkk/output-format :edn}))

(def baseline-answer
  (g/generate
   {:baseline-instruction
    (h/join
      "Answer the provided question as best as you can. Think carefuly about the question so as not to"
      "include any unverified information. Be consise but also do not omit any important details.")

    :baseline-answer
    (h/join
      "{{baseline-instruction}}"
      ""
      "QUESTION: {{question}}"
      "ANSWER: {% gen var-name=baseline-answer %}")

    :verification-plan-instruction
    (h/join
      "Analyze the QUESTION and ANSWER above. Think criticaly about the facts provided in the ANSWER."
      "For each mentioned fact, ask a question that would verify that fact."
      "Provide you response using Clojure EDN like this:"
      "[[\"mentioned fact 1\" \"verification question A\"]"
      " [\"mentioned fact 2\" \"verification question B\"]"
      " [\"mentioned fact 3\" \"verification question C\"]]")

    :verification-plan
    (h/join
      "QUESTION: {{question}}"
      "ANSWER: {{baseline-answer}}"
      ""
      "{{verification-plan-instruction}}"
      ""
      "{% gen var-name=verification-questions %}")

    :verification-plan-execution
    (h/join
      "Answer the flowing questions:"
      "{% for _fact, question in verification-questions %} {{question}}"
      "{% endfor %}"
      ""
      "Provide very succinct answers using Clojure EDN vector listing only answers in exact same order."
      "{% gen var-name=verification-answers %}")

    :validation
    (h/join
      "Given a list of QUESTION, CORRECT ANSWER and PROVIDED ANSWER, determine if the provided answer is correct."
      ""
      "{% for _provided-answer, question in verification-questions %} QUESTION {{forloop.counter}}. {{question}}"
      "{% endfor %}"
      ""
      "{% for provided-answer, _question in verification-questions %} PROVIDED ANSWER {{forloop.counter}}. {{provided-answer}}"
      "{% endfor %}"
      ""
      "{% for correct-answer in verification-answers %} CORRECT ANSWER {{forloop.counter}}. {{correct-answer}}"
      "{% endfor %}"
      ""
      "Give your estimation if PROVIDED ANSWER matches CORRECT ANSWER with 'true' or 'false' boolean values."
      "Combine the results of the corect answer, provided answer, and evaluation result Clojure EDN data structure like this:"
      "[{:question \"QUESTION\" :correct-answer \"CORRECT ANSWER\" :provided-answer \"PROVIDED ANSWER\" :result true}]"
      "{% gen var-name=validations %}")

    :revision
    (h/join
      "Given the corrected facts in REVSION reformulate original ANSWER using in a more truthful way."
      "QUESTION: {{question}}"
      "ANSWER: {{baseline-answer}}"
      ""
      "Revision of the facts stated in the ANSWER"
      "{% for revision in validations %}"
      "STATEMENT {{forloop.counter}}. '{{revision.provided-answer}}' is {{revision.result}}"
      "{% if not revision.result %}CORRECT FACT: {{revision.correct-answer}}{% endif %}"
      "{% endfor %}"
      ""
      "Given the above facts, revise the ANSWER to be more truthful."
      "{% gen var-name=revised-answer %}")}

   {:question "What was the primary cause of the Mexican-American war?"}
   {:baseline-answer        model-params
    :verification-questions model-edn-params
    :verification-answers   model-edn-params
    :validations            model-edn-params
    :revised-answer         model-params}))
