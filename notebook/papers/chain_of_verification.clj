^{:nextjournal.clerk/visibility {:code :hide}}
(ns papers.chain-of-verification
  (:require
   [bosquet.llm.generator :as g]
   [bosquet.wkk :as wkk]
   [helpers :as h]
   [nextjournal.clerk :as clerk]))

;; ## Chain of Verification prompting

;; Meta AI researchers in their [Chain-of-Verification Reduces Hallucination in Large Language Models](https://arxiv.org/pdf/2309.11495.pdf) paper
;; introduced a Chain of Verification (CoVe) prompting technique. It aims to reduce the amount of hallucination in the generated text.

;; > Generation of plausible yet incorrect factual information, termed hallucination, is an unsolved issue in large language models.
;; > We study the ability of language models to deliberate on the responses they give in order to correct their mistakes.

;; CoVe works in self-correcting steps:
;; 1. Generate a baseline answer to the question
;; 2. Use LLM to generate a set of questions that would verify the facts provided baseline answer
;; 3. Answer the generated questions using independent requests to LLMs, agents, or humans in the loop
;; 4. Compare review answers with the facts provided in the baseline answer
;; 5. Ask LLM to reformulate the baseline answer accounting for the corrections suggested in the review
;;
;; ![CoVe](notebook/assets/cove.png)

;; ## Implementation
;;
;; CoVe can be fully implemented in *Bosquet* using chained prompts, output coercion (EDN), and Selmer templating.
;;
;; First, helper configuration defs. Since CoVe works with GPT4 lets define one set of params for GPT4.
;; Another `model-end-params` is to be used for outputs needing EDN coercion.

^{:nextjournal.clerk/visibility {:result :hide}}
(def model-params {wkk/model-parameters {:model "gpt-4"}})
(def model-edn-params (merge model-params {wkk/output-format :edn}))

;; The prompt will reflect the CoVe steps:
;; - `baseline-instruction` is a prompt to instruct LLM how to provide the initial answer. I am using `*-instruction` prompts to separate plain instructions to make it easier to tweak them.
;; - `baseline-answer` defines how to generate and will invoke generation itself
;; - `verification-plan-instruction` is a prompt to instruct LLM how to generate verification questions. Note that it asks LLM to generate an EDN response, *Bosquet* will coerce it to EDN Clojure data structure.
;; - `verification-plan` defines how to generate verification questions and will invoke generation itself
;; - `verification-plan-execution` will list the generated questions and ask to answer them in EDN. Note that this generation will not see the `baseline-answer` and thus will not be influenced by it. (See *3.3 EXECUTE VERIFICATIONS* section, part *Factored* in the CoVe paper).
;; - `validation` is the place where facts provided in the baseline answer are compared to the answers from the verification step. This is also the spot where alternative validation techniques can be applied, for example via an Agent-based search tool.
;; - `revision` uses all the work done above and saved into EDN data from the `validation` step. With that LLM is instructed to rewrite the original answer.

^{:nextjournal.clerk/visibility {:result :hide}}
(def result
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
       "Given the corrected facts in REVISION reformulate original ANSWER using in a more truthful way."
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

;; ## Results
;;
;; *Bosquet* returns a map with all the intermediate results and the corrected answer.
;;
;; Bellow is the data showing CoVe thinking steps and verification results
;;
^{:nextjournal.clerk/visibility {:code :hide}}
(clerk/html
  [:div.font-mono
   [:div.flex.mb-4
    [:div.flex-none.w-32.mr-4 [:em "Question:"]]
    [:div (:question result)]]
   [:div.flex.mb-4
    [:div.flex-none.w-32.mr-4 [:em "Answer:"]]
    [:div (:baseline-answer result)]]
   [:div.flex.mb-4
    [:div.flex-none.w-32.mr-4 [:em "Validation:"]]
    (vec
      (cons
        :div
        (map-indexed
          (fn [idx [fact question]]
            [:div.block.p-6.bg-white.border.border-gray-200.rounded-lg.shadow.hover:bg-gray-100.dark:bg-gray-800.dark:border-gray-700.dark:hover:bg-gray-700.grid.grid-cols-1.gap-3
             [:div.flex
              [:div.flex-none.w-32.mr-4 [:em "Stated fact:"]]
              [:div fact]]
             [:div.flex
              [:div.flex-none.w-32.mr-4 [:em "Verification question:"]]
              [:div question]]
             [:div.flex
              [:div.flex-none.w-32.mr-4 [:em "Correct answer:"]]
              [:div (:correct-answer (get (:validations result) idx))]]
             [:div.flex
              [:div.flex-none.w-32.mr-4 [:em "Evaluation:"]]
              [:div (str (:result (get (:validations result) idx)))]]
             ])
          (:verification-questions result))))]])

;; And the revised answer based on the work done above
^{:nextjournal.clerk/visibility {:code :hide}}
(clerk/html
  [:div.block.p-6.bg-white.border.border-gray-200.rounded-lg.shadow.hover:bg-gray-100.dark:bg-gray-800.dark:border-gray-700.dark:hover:bg-gray-700.grid.grid-cols-1.gap-3
   [:div.flex
    [:div.flex-none.w-32.mr-4 [:em "Question:"]]
    [:div (:question result)]]
   [:div.flex
    [:div.flex-none.w-32.mr-4 [:em "Revised answer:"]]
    [:div (:revised-answer result)]]])
