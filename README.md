[![Clojars Project](https://img.shields.io/clojars/v/io.github.zmedelis/bosquet.svg)](https://clojars.org/io.github.zmedelis/bosquet)

# LLMOps for Large Language Model-based applications 

Bosquet is on a mission to make building AI applications simple. All nontrivial AI applications need to work with prompt templates that quickly grow in complexity, limited LLM context windows require memory management, and agents are needed for AI applications to interact with the outside world.

Bosquet provides instruments to work with those AI application concepts:
* LLM and Tool service management via [Integrant](https://github.com/weavejester/integrant)
* Prompt templating via integration with the excellent [Selmer](https://github.com/yogthos/Selmer) templating library
* Prompt chaining and composition via a powerful [Pathom](https://pathom3.wsscode.com/) graph processing machine
* Agent and tools definition abstractions for the interactions with external APIs.
* LLM memory handling (in progress, to be added in the next release)
* Other instruments like call response caching (see documentation)

## Documentation

[Full project documentation](https://zmedelis.github.io/bosquet/) (WIP)

## Use

Secrets like keys are stored in `secrets.edn` file and local parameters are kept in `config.edn`. Make a copy of `config.edn.sample` and `config.edn.sample` files. Change as needed.

### CLI

Run the following command do get CLI options
```
clojure -M -m bosquet.cli
```

Set the default model with
```
clojure -M -m bosquet.cli llms set --service openai --temperature 0 --model gpt-4
```

Do not forget to set the API KEY for your service (change 'openai' to different name if needed)
```
clojure -M -m bosquet.cli keys set openai
```

With that set, you can run generations:

```
clojure -M -m bosquet.cli "2+{{x}}="
```
Or using files
```
clojure -M -m bosquet.cli -p demo/play-writer-prompt.edn -d demo/play-writer-data.edn
```


### Prompt completion

Simple prompt completion can be done like this.

```colojure
(require '[bosquet.llm.generator :refer [generate llm]])

(generate "When I was 6 my sister was half my age. Now I’m 70 how old is my sister?")
=>
"When you were 6, your sister was half your age, which means she was 6 / 2 = 3 years old.\nSince then, there is a constant age difference of 3 years between you and your sister.\nNow that you are 70, your sister would be 70 - 6 = 64 years old."}
```


### Completion from the prompt map


```clojure
(require '[bosquet.llm :as llm])
(require '[bosquet.llm.generator :refer [generate llm]])

(generate
 llm/default-services
 {:question-answer "Question: {{question}}  Answer: {{answer}}"
  :answer          (llm :openai)
  :self-eval       ["Question: {{question}}"
                    "Answer: {{answer}}"
                    ""
                    "Is this a correct answer?"
                    "{{test}}"]
  :test            (llm :openai)}
 {:question "What is the distance from Moon to Io?"})
=>

{:question-answer
 "Question: What is the distance from Moon to Io?  Answer:",
 :answer
 "The distance from the Moon to Io varies, as both are orbiting different bodies. On average, the distance between the Moon and Io is approximately 760,000 kilometers (470,000 miles). However, this distance can change due to the elliptical nature of their orbits.",
 :self-eval
 "Question: What is the distance from Moon to Io?\nAnswer: The distance from the Moon to Io varies, as both are orbiting different bodies. On average, the distance between the Moon and Io is approximately 760,000 kilometers (470,000 miles). However, this distance can change due to the elliptical nature of their orbits.\n\nIs this a correct answer?",
 :test
 "No, the answer provided is incorrect. The Moon is Earth's natural satellite, while Io is one of Jupiter's moons. Therefore, the distance between the Moon and Io can vary significantly depending on their relative positions in their respective orbits around Earth and Jupiter."}

```

### Chat

``` clojure
(require '[bosquet.llm.wkk :as wkk])
(generate
    [[:system "You are an amazing writer."]
     [:user ["Write a synopsis for the play:"
             "Title: {{title}}"
             "Genre: {{genre}}"
             "Synopsis:"]]
     [:assistant (llm wkk/openai
                    wkk/model-params {:temperature 0.8 :max-tokens 120}
                    wkk/var-name :synopsis)]
     [:user "Now write a critique of the above synopsis:"]
     [:assistant (llm wkk/openai
                    wkk/model-params {:temperature 0.2 :max-tokens 120}
                    wkk/var-name     :critique)]]
    {:title "Mr. X"
    :genre "Sci-Fi"})
=>

#:bosquet{:conversation
          [[:system "You are an amazing writer."]
           [:user
            "Write a synopsis for the play:\nTitle: Mr. X\nGenre: Sci-Fi\nSynopsis:"]
           [:assistant "In a futuristic world where technology ..."]
           [:user "Now write a critique of the above synopsis:"]
           [:assistant
            "The synopsis for the play \"Mr. X\" presents an intriguing premise ..."]],
          :completions
          {:synopsis
           "In a futuristic world where technology has ...",
           :critique
           "The synopsis for the play \"Mr. X\" presents an intriguing premise set ..."}}
```

Generation returns `:bosquet/conversation` listing full chat with generated parts filled in, and `:bosquet/completions` containing only generated data

`

## Features

*Bosquet* relies on [Selmer](https://github.com/yogthos/Selmer) and [Pathom](https://pathom3.wsscode.com/) to implement composable prompts with 
advanced template definition functionality.

### Composability

Composability allows focusing on prompt language and logic, not worrying about resolving the dependencies and sequence of the prompt execution.

In this prompt definition, *Bosquet* will ensure the following sequence of execution:

1. First data needs to be filled in: *title* - "The Parade" and *style* - "Horror"
1. These are all the dependencies needed for *synopsis* generation, and at the place specified with `((bosquet.openai/complete))` an OpenAI API is called to get the results.
1. Once the *synopsis* is completed, the *review* can be done. The *synopsis/completion* dependency is automatically fulfilled and the *review* prompt `((bosquet.openai/complete))` will be called to produce the review 
1. Generated text for review will go under *review/completion* key.

### Templates

*Bosquet* uses [Selmer](https://github.com/yogthos/Selmer) to define its templates with all the functionality coming from Selmer's templating language:
* filters
* loops
* branches
* default values
to name a few.

### LLM Services

Currently, the following LLM APIs are supported
* OpenAI
* Cohere

See [Generation](#generation) section for service configuration.

### Agents

Initial support for working with Agents implements ReAct pattern and adds a Wikipedia tool to fulfill tasks.

#### Example code

```
(import '[bosquet.agent.wikipedia Wikipedia])
(def prompt-palette (template/load-palettes "resources/prompt-palette/agent"))
(def question
    "Author David Chanoff has collaborated with a U.S. Navy admiral who served as the ambassador to the United Kingdom under which President?")
(solve-task (Wikipedia.) prompt-palette {:task question})
```

`solve-task` call accepts:
- tool parameter (obvious next step is to provide a tool DB and the agent will pick the tool for work)
- `prompt-palette` defining prompt templates for the agent (see the section below)
- `parameters` defining the task, agent prompt template will define what parameters are needed

#### Prompt Template

ReAct oriented prompt template structure

* `prompt-palette` is where the ReAct flow is defined and where customizations can be made to fine-tune this to solve different tasks.
* `:react/examples` provides examples of how to solve tasks
* `:react/step-0` prompt template for the initialization of the task
* `:react/step-n` prompt template for subsequent thinking steps
