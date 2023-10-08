(ns math-generate-code
  (:require [bosquet.llm.generator :as g]
            [helpers :as h]
            [bosquet.system :as s]
            [nextjournal.clerk :as c]))


(def problem "This means she uses 3 + 4 = 7 eggs every day.
She sells the remainder for $2 per egg, so in
total she sells 7 * $2 = $14 per day. ")

(g/generate
  {:roger (h/join
            "Q: Roger has 5 tennis balls. He buys 2 more cans of tennis balls."
            "Each can has 3 tennis balls. How many tennis balls does he have now?"
            "A: {% gen var-name=A %}")}
  {}
  {:roger
   {:bosquet.llm/service          [:llm/openai :provider/openai]
    :bosquet.llm/model-parameters {:model "gpt-3.5-turbo"}}})

(g/generate
  {:roger
   (h/join
     "Q: Roger has 5 tennis balls. He buys 2 more cans of tennis balls."
     "Each can has 3 tennis balls. How many tennis balls does he have now?"
     "A: The answer is 11")
   :cafeteria
   (h/join
     "Q: The cafeteria had 23 apples. If they used 20 to make lunch and bought"
     "6 more, how many apples do they have?"
     "A: {% gen var-name=A %}")})
