(ns bosquet.prompt-pattern
  (:require
    [bosquet.template :as template]
    [bosquet.generator :as generator]))

(def prompt-patterns (template/load-edn "resources/prompt/prompt_template.edn"))

(def full-text :completion/full-text)

(def generated-text :completion/generated-text)

(def result-keys [full-text generated-text])

(defn- prompt-items [prompt-pattern]
  (merge
    (select-keys prompt-patterns [prompt-pattern])
    {full-text
     (str "{{" (str (.-sym prompt-pattern)) "}} ((bosquet.openai/get-completion))")}))

(defn summarize []
  (fn [text]
    (generator/complete
      (prompt-items :prompt-pattern/summarize)
      {:paragraph text}
      result-keys)))

(defn basic-qna [example-problem example-solution]
  (fn [problem]
    (generator/complete
      (prompt-items :prompt-pattern/basic-qna)
      {:prompt-example/problem  example-problem
       :prompt-example/solution example-solution
       :completion/problem      problem}
      result-keys)))

(defn chain-of-though
  "[Chain-of-Thought Prompting Elicits Reasoning in Large Language Models](https://arxiv.org/pdf/2201.11903.pdf)

  Good for:
  - arithmetic
  - commonsense
  - symbolic reasoning"
  [example-problem example-cot example-solution]
  (fn [problem]
    (generator/complete
      (prompt-items :prompt-pattern/cot)
      {:prompt-example/problem  example-problem
       :prompt-example/cot      example-cot
       :prompt-example/solution example-solution
       :completion/problem      problem}
      result-keys)))

(comment

  (def summarizer (summarize))
  (summarizer "Summary for this")

  (def roger-qna
    (basic-qna
      "Roger has 5 tennis balls. He buys 2 more cans of tennis balls.
Each can has 3 tennis balls. How many tennis balls does he have now?"
      "The answer is 11."))

  (roger-qna
    "The cafeteria had 23 apples. If they used 20 to make lunch and bought 6 more,
how many apples do they have?")

  (def roger-cot
    (chain-of-though
      "Roger has 5 tennis balls. He buys 2 more cans of tennis balls. Each can has 3 tennis balls. How many tennis balls does he have now?"
      "Roger started with 5 balls. 2 cans of 3 tennis balls each is 6 tennis balls. 5 + 6 = 11."
      "The answer is 11."))

  (roger-cot
    "The cafeteria had 23 apples. If they used 20 to make lunch and bought 6 more,
how many apples do they have?"))
