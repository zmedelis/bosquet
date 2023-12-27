(ns getting-started
  (:require [bosquet.llm.generator :as g]))

;; ## Getting Started
;;
;; Firt, you need to provide configuration parameters to make LLM service calls.
;; Find `config.edn.sample` at the root of the project, rename it to `config.edn`
;; and set necessary parameters. The `resources/env.edn` file shows how the config
;; is loaded and what defaults are available.

;; Generating LLM completions is as simple as calling `generate` function with a prompt.
;; This will use the defaults: OpenAI with GPT-3.5 modelto generate the completion.

(g/generate
  "When I was 6 my sister was half my age. Now I’m 70 how old is my sister?")

;; The simplest use case showing some of the basic Bouquet functionality is using linked prompt templates for text generation.

;; Let's say we want AI to generate two texts:
;; 1. a synopsis of the play from `title` and `genre` inputs
;; 1. a synopsis review.

;; Something like this:

;; > Title: City of Shadows
;; > Genre: Crime
;; > Synopsis:
;; > In the gritty underbelly of City of Shadows, corruption and crime reign supreme. The play follows the interconnected lives of several individuals, revealing the intricate web of deceit and darkness that engulfs the city. Detective Mark Johnson ...
;; >
;; > Review:
;; > Riveting Examination of Corruption and Justice
;; > Rating: ★★★★☆
;; > ...

;; To do this, let's define a template with a prompt and slots for data insertion. *Bosquet* uses a fork of [Selmer](https://github.com/zmedelis/Selmer) templating library for prompt definitions. *Selmer* provides rich templating language, but for now, all that is needed is its `{{DATA}}` syntax to mark spots in the text for data injection.

;; Linked templates are defined in a map, where the map key is a variable name that can be used to reference templates from each other.

(def template
  {:synopsis "You are a playwright. Given the play's title and it's genre
              it is your job to write synopsis for that play.
              Title: {{title}}
              Genre: {{genre}}
              {% gen play %}"
   :critique "You are a play critic from the Moon City Times.
              Given the synopsis of play, it is your job to write a review for that play.
              Play Synopsis:
              {{play}}
              Review from a New York Times play critic of the above play:
              {% gen review %}"})

;; Things to note:
;; * `gen` template tag marks the place where AI-generated text will be inserted; the `gen` tag uses the text above as its generation content
;; * `play` and `review` are the arguments to `gen` specifying the key in returned generation data holding the generation result
;; * `{{title}}` (and other slots in that form) is where supplied inputs will be injected

;; *Bosquet* will be invoking *OpenAI API* thus make sure that `OPENAI_API_KEY` is present as the environment variable.

;; With the prerequisite data set, let's run the generation.

(g/generate
 {:llm/service :openai}
 template
 {:title "City of Shadows" :genre "crime"})

;; This demonstrates a more typical way of calling `generate` function with the following parameters:
;; - first argument specifying the LLM service to use (see Configuration document for full details)
;; - second argument is the template map
;; - lastly the data to pass to the template

;; The output contains the map with:
;; * input data under `title` and `genre`
;; * complete template filling results under `synopsis ` and `critique` including template text and AI generation result
;; * AI generation only results under `gen` var name params
