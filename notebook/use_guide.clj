(ns use-guide
  (:require
    ;; FIXME this is an issue since openai is not evaluted
    ;; need to explicitly load it
    [bosquet.openai]
    [bosquet.generator :as gen]
    [nextjournal.clerk :as clerk]))

;; This is the tutorial showing how Bosquet constructs prompts and produces AI completions.
;;
;; ## Simple single template case
;;
;; Let's say we want to generate a synopsis of the play.
;;
;; All we know is the `title` of the play and the `genre` we want that play to be in.
;; Prompt template definition employs Selmer templating library.
;;
;; [Selmer](https://github.com/yogthos/Selmer) uses `{{DATA}}` syntax to specify where data needs to be injected.
;;
;; *Bosquet* adds to *Selmer* a specification of where AI generation calls should
;; happen. This is indicated with the `((bosquet.openai/complete))` construct.
;; It is a call to a function defined in `bosquet.openai` namespace.

(def synopsis-template
  "You are a playwright. Given the title of play and the desired style,
it is your job to write a synopsis for that title.

Title: {{title}}
Genre: {{genre}}

Playwright: This is a synopsis for the above play:
((bosquet.openai/complete))")

;; With the template defined generation can be called.
;; Bosquet will be invoking *OpenAI API* thus make sure that `OPENAI_API_KEY`
;; is present as the environment variable.

;; The call to generation function takes in three parameters:
;; - `template` defined above
;; - `data` is the data to fill in the template slots (`title` and `genre`)
;; optional
;; - `config` parameter, Bosquet as the default uses Ada model, for more accurate production deployments `{:model "text-davinci-003"}` configuration should be used

;; The generation comes back with a tuple where the *first* member will contain all
;; the text which got its slots filled in and generated completion.
;; The *second* member of the tuple will contain only the AI-completed part.
(def synopsis
  (gen/complete-template
    synopsis-template {:title "Mr. X" :genre "crime"}))

;; Full *Bosquet* produced text
(clerk/html [:pre (first synopsis)])

;; Just the AI completion part
(clerk/html [:pre (second synopsis)])

;; ## Generating from templates with dependencies
;;
;; With the play synopsis generated, we want to add a review of that play.
;; The review prompt template will depend on synopsis generation. With *Bosquet* we
;; do not need to worry about resolving the dependencies it will be done automatically.
;; The review prompt template contains familiar call to generation function and
;; a reference - `{{synopsys-completion}}` (explained bellow) - to generated text for synopsys.

(def review-template
  "You are a play critic from the New York Times.
Given the synopsis of play, it is your job to write a review for that play.

Play Synopsis:
{{synopsis-completion}}

Review from a New York Times play critic of the above play:
((bosquet.openai/complete))")

;; Both templates need to be added to a map for further processing

(def play
  {:synopsis synopsis-template
   :review   review-template})

;; To process this more advanced case of templates in the dependency graph,
;; *Bosquet* provides the `gen/complete` function taking:
;; * `prompts` map defined above
;; * `data` to fill in fixed slots (Selmer templating)
;; * `config` configuration for the generation API
;; * and the `data-keys` we want to get back (TODO API needs simplification)

(def review (gen/complete play {:title "Mr. X" :genre "crime"} nil))

;; Generated review including 'synopsis' generation
(clerk/html [:pre (:review-completed review)])
