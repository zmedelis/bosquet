(ns bosquet.prompt-palette
  (:require
   [bosquet.template.read :as template]
   [bosquet.generator :as generator]))

(def palettes (template/load-palettes "resources/prompt-palette"))

(defn generator
  "Create a generator for named `prompt-pattern`.
  The `intro-data` contains static parts of the prompt: intiation text, examples, etc
  it will be reused with each call for different completions."
  ([palette-key intro-data]
   (fn [data]
     (generator/complete
      palettes
      (merge intro-data data)
      [palette-key])))
  ([prompt-pattern] (generator prompt-pattern nil)))

(comment

  (def roger-qna
    (generator
     :problem-solver/basic-qna
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
     :problem-solver/cot
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
