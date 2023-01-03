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


(defn generator
  "Create a generator for named `prompt-pattern`.
  The `intro-data` contains static part of the prompt: intiation text, examples, etc
  it will be reused with each call for different completions."
  ([prompt-pattern intro-data]
   (fn [data]
     (generator/complete
       (prompt-items prompt-pattern)
       (merge intro-data data)
       result-keys)))
  ([prompt-pattern]
   (generator prompt-pattern nil)))

#_(defn chain-of-though
  "[Chain-of-Thought Prompting Elicits Reasoning in Large Language Models](https://arxiv.org/pdf/2201.11903.pdf)

  Good for:
  - arithmetic
  - commonsense
  - symbolic reasoning")

(comment

  (def summarizer (generator :prompt-pattern/summarize))
  (summarizer {:paragraph "Once upon the time three things happened."})

  (def roger-qna
    (generator
      :prompt-pattern/basic-qna
      {:prompt-example/problem
       "Roger has 5 tennis balls. He buys 2 more cans of tennis balls.
Each can has 3 tennis balls. How many tennis balls does he have now?"
       :prompt-example/solution
       "The answer is 11."}))

  (roger-qna
    {:completion/problem
     "The cafeteria had 23 apples. If they used 20 to make lunch and bought 6 more,
how many apples do they have?"})

  (def roger-cot
    (generator
      :prompt-pattern/cot
      {:prompt-example/problem
       "Roger has 5 tennis balls. He buys 2 more cans of tennis balls. Each can has 3 tennis balls.
How many tennis balls does he have now?"
       :prompt-example/cot
       "Roger started with 5 balls. 2 cans of 3 tennis balls each is 6 tennis balls. 5 + 6 = 11."
       :prompt-example/solution
       "The answer is 11."}))

  (roger-cot
    {:completion/problem
     "The cafeteria had 23 apples. If they used 20 to make lunch and bought 6 more,
how many apples do they have?"}))
