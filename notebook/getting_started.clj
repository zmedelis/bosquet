(ns getting-started
  (:require
   [bosquet.llm :as llm]
   [bosquet.llm.generator :as g]
   [bosquet.utils :as u]))

;; ## Getting Started
;;
;; First, you need to provide configuration parameters to make LLM service calls.
;; Find `config.edn.sample` at the root of the project, rename it to `config.edn`
;; and set necessary parameters. The `resources/env.edn` file shows how the config
;; is loaded and what defaults are available.
;;
;; ### Simple prompt

;; Generating LLM completions is as simple as calling `generate` function with a prompt.
;; This will use the defaults: OpenAI with GPT-3.5 model to generate the completion.

^{:nextjournal.clerk/auto-expand-results? true}
(g/generate
 "When I was 6 my sister was half my age. Now I’m 70 how old is my sister?")

;; ### Prompt composition
;; A use case showing some of the basic Bouquet functionality is using linked prompt templates for text generation.
;;
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
  {:synopsis (u/join-nl "You are a playwright. Given the play's title and it's genre"
                        "it is your job to write synopsis for that play."
                        "Title: {{title}}"
                        "Genre: {{genre}}")
   :play     (g/llm :openai llm/context :synopsis)
   :critique "You are a play critic from the Moon City Times.
              Given the synopsis of play, it is your job to write a review for that play.
              Play Synopsis:
              {{play}}
              Review from a New York Times play critic of the above play:"
   :review   (g/llm :openai llm/context :critique)})

;; Things to note:
;; * `play` and `review` define generation points, there you specify which LLM to use and which value from the map will be used as prompt context
;; * `{{title}}` (and other slots in that form) is where supplied inputs will be injected

;; *Bosquet* will be invoking *OpenAI API* thus make sure that `OPENAI_API_KEY` is present as the environment variable.

;; With the prerequisite data set, let's run the generation.

^{:nextjournal.clerk/auto-expand-results? true}
(g/generate template {:title "City of Shadows" :genre "crime"})
