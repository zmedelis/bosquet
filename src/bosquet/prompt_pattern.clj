(ns bosquet.prompt-pattern
  (:require
    [bosquet.template :as template]
    [bosquet.generator :as generator]))

(def palettes (template/load-palettes "resources/prompt-palette"))

(defn generator
  "Create a generator for named `prompt-pattern`.
  The `intro-data` contains static parts of the prompt: intiation text, examples, etc
  it will be reused with each call for different completions."
  ([palette-key intro-data config]
   (fn [data]
     (generator/complete
       (select-keys palettes [palette-key])
       (merge intro-data data)
       config)))
  ([prompt-pattern intro-data] (generator prompt-pattern intro-data nil))
  ([prompt-pattern] (generator prompt-pattern nil nil)))

(comment

  (def summarizer (generator :text-analyzer/summarize-to-sentence))
  (summarizer {:text-type "paragraph"
               :text      "Once upon the time three things happened."})

  (def sentimental
    (generator :text-analyzer/assess-sentiment
      nil
      {:model "text-davinci-003"}))

  (sentimental
    {:text-type "tweet"
     :text      "How did everyone feel about the Climate Change question last night? Exactly."})

  (def sentimental-batch
    (generator :text-analyzer/assess-sentiment-batch
      nil
      {:model "text-davinci-003"}))

  (sentimental-batch
    {:text-type "tweets"
     :text
     ["How did everyone feel about the Climate Change question last night? Exactly."
      "Didn't catch the full #GOPdebate last night. Here are some of Scott's best lines in 90 seconds."
      "The biggest disappointment of my life came a year ago."]})

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
