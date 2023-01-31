# LLMOps for Large Language Model based applications 

![bosquet chain](/doc/img/generation-chain.png)

All but most trivial LLM applications require complex prompt handling, development, evaluation, secure use, and deployment techniques.
Bosquet is building LLMOps functionality (see the tutorial bellow for the parts that are now implemented):

## Quick example

An example of a composable prompt definition to define a prompt answering question with role assumption.

```clojure
(complete
  ;Prompt components
  {:role      "As a brilliant {{who-you-are}} answer the following question."
   :QnA       "{{role-completed}} {{question}} Answer: {% llm-generate var-name=answer %}"
   :self-eval "{{QnA-completed}} Is this a correct answer? {% llm-generate var-name=test%}"}
   ;Data
   {:who-you-are "astronomer"
    :question    "What is the distance from Moon to Io?"})
=>
{:role-completed
 "As a brilliant astronomer answer the following question."
 
 :QnA-completed
 "As a brilliant astronomer answer the following question. What is the distance from Moon to Io? 
  Answer: The average distance between the Moon and Io is approximately 384,400 km (238,855 mi)."
  
 :QnA-completion
 {:answer
  "The average distance between the Moon and Io is approximately 384,400 km (238,855 mi)."}
  
 :self-eval-completed
 "As a brilliant astronomer answer the following question. What is the distance from Moon to Io? 
  Answer: The average distance between the Moon and Io is approximately 384,400 km (238,855 mi). 
  Is this a correct answer? Yes, that is a correct answer."
  
 :self-eval-completion {:test "Yes, that is a correct answer."}}

```

## Planned features

* Support access to all main LLM **models**: [GPT](https://openai.com/api/), [Bloom](https://bigscience.huggingface.co/blog/bloom), and [Stable Diffusion](https://stability.ai/blog/stable-diffusion-v2-release) to start with.
* Provide scaffolding for prompt building **methods**: Role Promoting, Chain of Thought, Zero-Shot CoT, Self Consistency, and more.
* **Vulnerability** assessment and monitoring. How possible are prompt leak or injection attacks? Can prompt generate harmful content?
* Prompt quality **evaluation**.
* Developed and tested prompt **deployment** to [Cloudflare Workers](https://workers.cloudflare.com/), [AWS Lambda](https://aws.amazon.com/lambda/), or self-host via REST API.
* Prompt service **reliability** guarantees.

## Current Features

*Bosquet* relies on [Selmer](https://github.com/yogthos/Selmer) and [Pathom](https://pathom3.wsscode.com/) to implement composable prompts with 
advanced template definition functionality.

### Composability

Composability allows focusing on prompt language and logic, not worrying about resolving the dependencies and sequence of the prompt execution.

![prompt chaining](/doc/img/chained-generation.png)

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

![selmer template](/doc/img/selmer-template.png)

## Instalation

One time action need to prep the libs

```bash
clj -X:deps prep
```


## Defining and executing prompts

This section is available as a live via [Clerk](https://clerk.vision/) notebook.
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

Bosquet will be invoking *OpenAI API* thus make sure that `OPENAI_API_KEY`
is present as the environment variable.

`llm-generate` call to the *OpenAI* will use configuration parameters specfied
in that tag and reflect parameters specified by [Open AI API](https://beta.openai.com/docs/api-reference/completions).
The tag uses the same names. If config parameters are not used, then defaults
are used. Note that the default model is *Ada*, in production *Davinci* would be a
natural choice.

```clojure
{model             ada
 temperature       0.6
 max-tokens        80
 presence-penalty  0.4
 frequence-penalty 0.2
 top-p             1
 n                 1}
```

The call to generation function takes in:
- `template` defined above
- `data` is the data to fill in the template slots (`title` and `genre`)

The generation comes back with a tuple where the *first* member will contain all
the text which got its slots filled in and generated completion.
The *second* member of the tuple will contain only the AI-completed part.

``` clojure
(def synopsis
  (gen/complete-template
    synopsis-template
    {:title "Mr. X" :genre "crime"}))

```

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
(def review (gen/complete play {:title "Mr. X" :genre "crime"}))
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
                  {:text-type "tweets" :tweets tweets}))
```
