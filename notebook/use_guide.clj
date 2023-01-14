(ns use-guide
  (:require
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
(clerk/html [:blockquote (first synopsis)])

;; Just the AI completion part
(clerk/html [:blockquote (first synopsis)])
