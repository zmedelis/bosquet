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

[Full project documentation](https://zmedelis.github.io/bosquet/notebook/getting_started/index.html) (WIP)

## Quick example

An example of a composable prompt definition. It is a prompt to answer a question with a 'role assumption' pattern.

Secrets and other local parameters are kept in `config.edn`. Make a copy of `config.edn.sample` and enter your account API KEYS from OpenAI, Cohere,
or other providers.


```clojure
(require '[bosquet.generator :as bg])

(bg/generate
   {:role            "As a brilliant {{you-are}} answer the following question."
    :question        "What is the distance between Io and Europa?"
    :question-answer "Question: {{question}}  Answer: {% gen var-name=answer %}"
    :self-eval       "{{answer}} Is this a correct answer? {% gen var-name=test model=text-curie-001 %}"}
   {:you-are  "astronomer"
    :question "What is the distance from Moon to Io?"})
=>
{:you-are "astronomer",
 :question "What is the distance from Moon to Io?",
 :question-answer
 "Question: What is the distance from Moon to Io?  Answer: The distance from Earth to Io is about 93,000 miles.",
 :answer "The distance from Earth to Io is about 93,000 miles.",
 :self-eval
 "The distance from Earth to Io is about 93,000 miles. Is this a correct answer? The distance from Earth to Io is about 93,000 miles.",
 :test "The distance from Earth to Io is about 93,000 miles."}
```

## Features

*Bosquet* relies on [Selmer](https://github.com/yogthos/Selmer) and [Pathom](https://pathom3.wsscode.com/) to implement composable prompts with 
advanced template definition functionality.

### Composability

Composability allows focusing on prompt language and logic, not worrying about resolving the dependencies and sequence of the prompt execution.

In this prompt definition, *Bosquet* will ensure the following sequence of execution:

1. First data needs to be filled in: *title* - "The Parade" and *style* - "horror"
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
