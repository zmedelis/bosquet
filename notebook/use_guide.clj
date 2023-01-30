(ns use-guide
  (:require
    [bosquet.generator :as gen]
    [nextjournal.clerk :as clerk]))

;; This is the tutorial showing how Bosquet constructs prompts and produces AI completions.
;;
;; ## Simple single template case
;;
;; Let's say we want to generate a synopsis of the play from
;; its `title` and the `genre` we want that play to be in.
;;
;; ### Selmer templating language
;;
;; Prompt template definition is done with [Selmer](https://github.com/zmedelis/Selmer) templating library.
;; It uses `{{DATA}}` syntax to specify where data needs to be injected.
;;
;; *Bosquet* adds to *Selmer* a specification of where AI generation calls should
;; happen. This is indicated with the `{% llm-generate %}` [tag](https://github.com/zmedelis/Selmer#tags).
;; Behind that tag a completion function defined in `bosquet.openai` namespace will be alled.
;;
;; ### Synopsis template

(def synopsis-template
  "You are a playwright. Given the title of play and the desired style,
it is your job to write a synopsis for that title.

Title: {{title}}
Genre: {{genre}}

Playwright: This is a synopsis for the above play:
{% llm-generate model=text-davinci-003 var-name=synopsis%}")

;; ### Generation
;;
;; Bosquet will be invoking *OpenAI API* thus make sure that `OPENAI_API_KEY`
;; is present as the environment variable.
;;
;; `llm-generate` call to the *OpenAI* will use configuration parameters specfied
;; in that tag and reflect parameters specified by [Open AI API](https://beta.openai.com/docs/api-reference/completions).
;; The tag uses the same names. If config parameters are not used, then defaults
;; are used. Note that default model is *Ada*, in production *Davinci* would be a
;; nautural choice.
;;
;; ```clojure
;; {model             ada
;;  temperature       0.6
;;  max-tokens        80
;;  presence-penalty  0.4
;;  frequence-penalty 0.2
;;  top-p             1
;;  n                 1}
;; ```
;;
;; Note the optional `var-name` parameter. This is the name of the var to hold
;; generation generation result and it can an be used as a reference in other templates
;; or the same template further down.
;;
;; The call to generation function takes in:
;; - `template` defined above
;; - `data` is the data to fill in the template slots (`title` and `genre`)

;; The generation comes back with a tuple where the *first* member will contain all
;; the text which got its slots filled in and generated completion.
;; The *second* member of the tuple will contain only the AI-completed part.
(def synopsis
  (gen/complete-template
    synopsis-template
    {:title "Mr. X" :genre "crime"}))

;; Full *Bosquet* produced text
(clerk/html [:code.language-html (first synopsis)])

;; Just the AI completion part
(clerk/html [:code.language-html (-> synopsis second :synopsis)])

;; ## Generating from templates with dependencies
;;
;; With the play synopsis generated, we want to add a review of that play.
;; The review prompt template will depend on synopsis generation. With *Bosquet* we
;; do not need to worry about resolving the dependencies it will be done automatically.
;;
;; The review prompt template contains familiar call to generation function and
;; a reference - `{{synopsis-completion.synopsys}}` - to generated text for synopsys.
;;
;; The reference points to `synopsis-completion.synopsis` where `synopsis-completion`
;; points to the map of all completions done by `synopsis` template and `.synopsis`
;; pics out the `var-name` used for that specific generation.

(def review-template
  "You are a play critic from the New York Times.
Given the synopsis of play, it is your job to write a review for that play.

Play Synopsis:
{{synopsis-completion.synopsis}}

Review from a New York Times play critic of the above play:
{% llm-generate model=text-davinci-003 %}")

;; Note that `var-name` is not used and generaation will be assigned to a
;; default `llm-generate` name.
;;
;; Both templates need to be added to a map for further processing

(def play
  {:synopsis synopsis-template
   :review   review-template})

;; To process this more advanced case of templates in the dependency graph,
;; *Bosquet* provides the `gen/complete` function taking:
;; * `prompts` map defined above
;; * `data` to fill in fixed slots (Selmer templating)

(def review (gen/complete play {:title "Mr. X" :genre "crime"}))

;; Generated review including 'synopsis' generation
(clerk/html [:code.language-html (:review-completed review)])
