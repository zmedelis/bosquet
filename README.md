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

![bosquet chain](/docs/img/generation-chain.png)

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

![prompt chaining](/docs/img/chained-generation.png)

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

A template example using for loop to fill in the data passed in as a collection

![selmer template](/docs/img/selmer-template.png)

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


## Instalation

One-time action to prep the libs

```bash
clj -X:deps prep
```


## Defining and executing prompts

This section is available as a live via **[Clerk](https://clerk.vision/) notebook**.
Start project REPL with

```bash
clj -A:dev
```
to get it running on http://localhost:7777

**Bosquet** provides the following core functionality:
- defines prompt *templates*
- resolves *dependencies* between prompts
- produces AI *completions*

The following section will show how to use it.

## Simple single template case

Let's say we want to generate a synopsis of the play. The synopsis
is to be generated from `title` and `genre` inputs.

### Selmer templating language

Prompt template definition is done with [Selmer](https://github.com/zmedelis/Selmer) templating library.
It uses `{{DATA}}` syntax to specify where data needs to be injected.

*Bosquet* adds to *Selmer* a specification of where AI generation calls should
happen. This is indicated with the `{% llm-generate %}` [tag](https://github.com/zmedelis/Selmer#tags).

Generation is done with the following *Bosquet* features:
- `llm-generate` will receive all the text preceding it with already filled-in template slots. This text is used as the prompt to be sent to the completion API.
- `bosquet.openai` namespace defines completion function, that calls *OpenAI API* to initiate the completion

### Synopsis template

``` clojure
(def synopsis-template
  "You are a playwright. Given the play's title and t's genre
it is your job to write synopsis for that play.

Title: {{title}}
Genre: {{genre}}

Playwright: This is a synopsis for the above play:
{% llm-generate model=text-davinci-003 var-name=play %}")

```

Note the optional `var-name` parameter. This is the name of the var to hold generation result and it can be used as a reference in other templates or the same template further down. If `var-name` is not specified `"llm-generate"` will be used as the name.

### Generation

Bosquet will be invoking *OpenAI API* thus make sure to specify the correct model params including API keys.

`gen` call to the *OpenAI* will use configuration parameters specified
in that tag and reflect parameters specified by [Open AI API](https://beta.openai.com/docs/api-reference/completions).
The tag uses the same names. If config parameters are not used, then defaults
are used. Note that the default model is *Ada*, in production *GPT-3.5* would be a
better choice.


```

The call to generation function takes in:
- `template` defined above
- `data` is the data to fill in the template slots (`title` and `genre`)
- `params` optional parameters specifying which service to use, merge with config from the tag

The generation comes back with a tuple where the *first* member will contain all
the text that got its slots filled in and generated completion.
The *second* member of the tuple will contain only the AI-completed part.

``` clojure
(def synopsis
  (gen/complete-template
    synopsis-template
    {:title "Mr. X" :genre "crime"}
    {:play   :llm/openai
     :review :llm/cohere}))

```

Note that in the above example different LLMs are used to generate different parts of the prompt.

For more details on how system configuration is done refer to [User Guide notebook](https://github.com/zmedelis/bosquet/blob/main/notebook/user_guide.clj) 
section - *System Configuration*

## Generating from templates with dependencies

With the play synopsis generated, we want to add a review of that play.
The review prompt template will depend on synopsis generation. With *Bosquet* we
do not need to worry about resolving the dependencies it will
be done automatically (powered by [Pathom](https://pathom3.wsscode.com/)).

### Review prompt

``` clojure
(def review-template
  "You are a play critic from the New York Times.
Given the synopsis of play, it is your job to write a review for that play.

Play Synopsis:
{{synopsis-completion.synopsis}}

Review from a New York Times play critic of the above play:
{% llm-generate model=text-davinci-003 %}")
```

Both templates need to be added to a map to be jointly processed by *Bosquet*.

``` clojure
(def play
  {:synopsis synopsis-template
   :review   review-template})
```


The review prompt template contains a familiar call to generation function and
a reference - `{{synopsis-completion.synopsis}}` - to a generated text for the synopsis.

The reference points to `synopsis-completion.synopsis` where `synopsis-completion`
points to the map of all completions done by `synopsis` template and `.play`
pics out the `var-name` used for that specific generation
(multiple generations can be defined in one template).

Thus the references between prompts and completions are constructed using this pattern

`[prompt-map-key]-completion.[var-name]`

To process this more advanced case of templates in the dependency graph,
*Bosquet* provides the `gen/complete` function taking:
* `prompts` map defined above
* `data` to fill in fixed slots (Selmer templating)

``` clojure
(def review (gen/complete play 
    {:title "Mr. X" :genre "crime"}
    {:synopsis {:llm-generate {:impl :openai :api-key "<my-key>"}}
     :review   {:llm-generate {:impl :openai :api-key "<my-key>"}}}))
```
In this case the model parameter can be specified as seen in a nested map using the template key, e.g.:

```clojure
(gen/complete play 
  {:title "Mr. X" :genre "crime"}
  {:synopsis { :llm-generate my-model-params}})
```


## Advanced templating with Selmer

[Selmer](https://github.com/yogthos/Selmer) provides lots of great templating functionality.
An example of some of those features.

### Tweet sentiment batch processing

Let's say we want to get a batch sentiment processor for Tweets.

A template for that:

``` clojure
(def sentimental
  "Estimate the sentiment of the following batch of {{text-type|default:text}} as positive, negative or neutral:
{% for t in tweets %}
* {{t}}
{% endfor %}

Sentiments:
{% llm-generate model=text-davinci-003 %}")
```

First, *Selmer* provides ['for' tag](https://github.com/yogthos/Selmer#for)
to process collections of data.

Then, `{{text-type|default:text}}` shows how defaults can be used. In this case,
if `text-type` is not specified `"text"` will be used.

Tweets to be processed

``` clojure
(def tweets
   ["How did everyone feel about the Climate Change question last night? Exactly."
    "Didn't catch the full #GOPdebate last night. Here are some of Scott's best lines in 90 seconds."
    "The biggest disappointment of my life came a year ago."])

(def sentiments (gen/complete-template sentimental
                  {:text-type "tweets" :tweets tweets}
                  ... model options ....))
```
