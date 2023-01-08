# Pre release work in progress

----

# Bosquet: LLMOps for Large Language Model based applications 

![bosquet](https://upload.wikimedia.org/wikipedia/commons/thumb/4/4f/42_Apollo_in_bosquet_F%C3%A4cher%2C_gardens_of_Sch%C3%B6nbrunn_03.jpg/640px-42_Apollo_in_bosquet_F%C3%A4cher%2C_gardens_of_Sch%C3%B6nbrunn_03.jpg)

All but most trivial LLM applications require complex prompt handling, development, evaluation, and deployment techniques. Bosquet is building functionality to aid with those challenges:
* Support access to all main LLM models: [GPT](https://openai.com/api/), [Bloom](https://bigscience.huggingface.co/blog/bloom), and [Stable Diffusion](https://stability.ai/blog/stable-diffusion-v2-release) to start with.
* Provide scaffolding for prompt building methods: Role Promoting, Chain of Thought, Zero-Shot CoT, Self Consistency, and more.
* Vulnerability assessment and monitoring. How possible are prompt leak or injection attacks? Can prompt generate harmful content?
* Prompt quality evaluation.
* Developed ant tested prompt deployment to [Cloudflare Workers](https://workers.cloudflare.com/), [AWS Lambda](https://aws.amazon.com/lambda/), or self host via REST API.

## Usage

Prompts are defined in a map. Items on the map can refer to each other. Bosquet will traverse the dependency graph and insert referred items where needed.

### Step by step

Let's say we are defining a [chain of though prompt] (https://learnprompting.org/docs/intermediate/chain_of_thought). We will need: example problem statement, example chain of though, example answer.

```clojure
(def cot-example 
  {:example/problem 
  "Roger has 5 tennis balls. He buys 2 more cans of tennis balls. 
  Each can has 3 tennis balls. How many tennis balls does he have now?"

  :example/chain-of-though
  "Roger started with 5 balls. 2 cans of 3 tennis balls each is 6 tennis balls.
  5 + 6 = 11."

  :example/soluton
  "The answer is 11."

  :prompt-pattern/chain-of-though
  "Problem:  {{example/problem}}
  Solution: {{example/chain-of-thought}} {{example/solution}}" 

  :completion/math-problem
  "{{prompt-pattern/chain-of-though}} 
  Problem:  {{math/problem}}
  ((bosquet.openai/complete))"})
```
With this CoT prompt definition, a function to generate completions for new problems can be defined.

``` clojure
(defn example-cot [problem]
  (fn [problem]
    (generator/complete
      cot-example
      {:math/problem problem}
      [:math/completion])))
```

With this we can get solutions for various math problems. It will always use the same
CoT patterns defined in `cot-example`

``` clojure
(example-cot 
  "The cafeteria had 23 apples. If they used 20 to make lunch and bought 6 more, 
  how many apples do they have?")
=>
"Solution: The cafeteria had 23 apples. They used 20 apples to make lunch, leaving them with 3 apples. They bought 6 more, so they now have 9 apples. The answer is 9."

```

### Predefined prompting methods

Bosquet provides predefined prompting patterns. For example, the above-described Chain of Thought can be done with the:

``` clojure
(require '[bosquet.prompt-pattern :as pp])

(def roger-cot
  (pp/chain-of-though
    "Roger has 5 tennis balls. He buys 2 more cans of tennis balls. Each can has 3 tennis balls. How many tennis balls does he have now?"
    "Roger started with 5 balls. 2 cans of 3 tennis balls each is 6 tennis balls. 5 + 6 = 11."
    "The answer is 11."))

(roger-cot 
  "The cafeteria had 23 apples. If they used 20 to make lunch and bought 6 more, how many apples do they have?")
```

