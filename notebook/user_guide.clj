(ns user-guide
  (:require [bosquet.llm.generator :as bg]
            [bosquet.system :as system]
            [nextjournal.clerk :as clerk]))

;; # Bosquet Tutorial
;;
;; This notebook will demonstrate the following things:
;; - system configuration
;; - define prompt *templates*
;; - resolve *dependencies* between prompts
;; - produce AI *completions*.
;;
;; ## System Configuration
;;
;; *Bosquet* uses [Integrant](https://github.com/weavejester/integrant) to setup its
;; components. With [Aero](https://github.com/juxt/aero) brining in some nice configuration
;; setup features to make it easier to declare and use configuration.
;;
;; Prensently *Bosquet* defines two types of components:
;; 1. LLM services
;; 2. Tools for the Agnets to use
;;
;; ### LLM services
;;
;; LLM service componens wrap access to the APIs provided by the likes of *OpenAI*, *Cohere*,
;; maybe localy deployed llamas.
;;
;; Let's take as an example OpenAI model access on MS Azure
;;
;;```edn
;;[:llm/openai :provider/azure]
;;{:api-key      #ref [:config :azure-openai-api-key]
;; :api-endpoint #or [#env "AZURE_OPENAI_API_ENDPOINT"
;;                    #ref [:config :azure-openai-api-endpoint]]
;; :impl         :azure}
;; ```
;;
;; As per *Integrant* setup `[:llm/openai :provider/azure]` is the key to the component, the
;; value specifies all the properties needed to start it up. Note the `#or` construct coming
;; from *Aero* which allows to take values either from environment variables or from the
;; config file.
;;
;; See the text generation examples bellow on how to use LLM components to generate text.
;; *Bosquet* allows you to change the LLM in use while processing the chained prompts.
;;
;; ## A simple single template case
;;
;; Let's say we want to generate a synopsis of the play, based only on the `title` and `genre` we want this play to be in.
;; Something like this:

^{:nextjournal.clerk/visibility {:code :hide}}
(clerk/html [:div.whitespace-pre-line.max-w-wide.bg-white.p-4.text-slate-500.text-sm
             "Title:
The Fifth Man

Genre:
Crime Drama

Synopsys:
The Fifth Man is a suspenseful crime drama set in a small town in the USA. Five childhood friends, now in their mid-twenties, have grown up together and are as close as brothers. One evening, the group is out at a nightclub wherein the lead of the group, John, finds an envelope stuffed with hundreds of thousands of dollars. Despite their better judgment, John and his friends decide to keep the money and use it to fund a life of pleasure and excitement."])

;; For this we will need to define a template with prompt and slots for data insertion.
;;
;; ### Selmer templating language
;;
;; Prompt template definition is done with [Selmer](https://github.com/zmedelis/Selmer) templating library.
;; It uses `{{DATA}}` syntax to specify where data needs to be injected.
;;
;; *Bosquet* adds to *Selmer* a specification of where AI generation calls should
;; happen. This is indicated with the `{% gen %}` [tag](https://github.com/zmedelis/Selmer#tags).
;;
;; Generation is done with the following *Bosquet* features:
;; - `gen` will recieve all the text preceeding it with already filled in template slots. This text
;; is used as the prompt to be sent to the competion API.
;; - `system` defines completion components implementing API call to LLM provider
;;
;; ### Synopsis template

(def synopsis-template
  "You are a playwright. Given the play's title and it's genre
it is your job to write synopsis for that play.

Title: {{title}}
Genre: {{genre}}

Playwright: This is a synopsis for the above play:
{% gen model=gpt-3.5 var-name=play %}")


;; Note the optional `var-name` parameter. This is the name of the var to hold
;; generation generation result and it can an be used as a reference in other templates
;; or the same template further down. If `var-name` is  not specified `gen` will be
;; used as the name.

;; ### Generation
;;
;; Bosquet will be invoking *OpenAI API* thus make sure that `OPENAI_API_KEY`
;; is present as the environment variable.
;;
;; `gen` call to the *OpenAI* will use configuration parameters specfied
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
;; The call to generation function takes in:
;; - `template` defined above
;; - `data` is the data to fill in the template slots (`title` and `genre`)

;; The generation comes back with a tuple where the *first* member will contain all
;; the text which got its slots filled in and generated completion.
;; The *second* member of the tuple will contain only the AI-completed part.
(def synopsis
  (bg/complete-template
   synopsis-template
   {:title "Mr. X" :genre "crime"}))

;; #### Full *Bosquet* produced text
^{::clerk/visibility {:code :hide}}
(clerk/html [:div.whitespace-pre-line.max-w-wide.bg-white.p-4.text-slate-500.text-sm  (first synopsis)])

;; #### Just the AI completion part
^{::clerk/visibility {:code :hide}}
(clerk/html [:div.whitespace-pre-line.max-w-wide.bg-white.p-4.text-slate-500.text-sm  (-> synopsis second :play)])

;; ## Generating from templates with dependencies
;;
;; With the play synopsis generated, we want to add a review of that play.
;; The review prompt template will depend on synopsis generation. With *Bosquet* we
;; do not need to worry about resolving the dependencies it will
;; be done automatically (powered by [Pathom](https://pathom3.wsscode.com/)).

;; ### Review prompt

(def review-template
  "You are a play critic from the New York Times.
Given the synopsis of play, it is your job to write a review for that play.

Play Synopsis:
{{play}}

Review from a New York Times play critic of the above play:
{% gen model=gpt-3.5 var-name=review %}")

;; Both templates need to be added to a map to be jointly processed by *Bosquet*.

(def play-review
  {:synopsis  synopsis-template
   :evrything review-template})

;; The review prompt template contains familiar call to generation function and
;; a reference - `{{play}}` - to generated text for the synopsis.
;;
;; To process this more advanced case of templates in the dependency graph,
;; *Bosquet* provides the `gen/complete` function taking:
;; * `prompts` map defined above
;; * `data` to fill in fixed slots (Selmer templating)

(def review (bg/generate
              play-review
              {:title "Mr. X" :genre "crime"}
              {:synopsis  (system/openai)
               :evrything (system/openai)}))

;; ### Fully generated review
^{::clerk/visibility {:code :hide}}
(clerk/html [:div.whitespace-pre-line.max-w-wide.bg-white.p-4.text-slate-500.text-sm (:evrything review)])


;; ### Just a review part for the generated play synopsis
^{::clerk/visibility {:code :hide}}
(clerk/html [:div.whitespace-pre-line.max-w-wide.bg-white.p-4.text-slate-500.text-sm  (:review review)])


;; ## Advanced templating with Selmer
;;
;; [*Selmer*](https://github.com/yogthos/Selmer) provides lots of great templating
;; functionality. An example of some of those features.
;;
;; ### Tweet sentiment batch processing
;;
;; Lets say we want to get a batch sentiment processor for Tweets.
;;
;; A template for that:

(def sentimental
  "Estimate the sentiment of the following batch of {{text-type|default:text}} as positive, negative or neutral:
{% for t in tweets %}
* {{t}}
{% endfor %}

Sentiments:
{% gen model=gpt-3.5 %}")

;; First, *Selmer* provides [for tag](https://github.com/yogthos/Selmer#for)
;; to process collections of data.
;;
;; Then, `{{text-type|default:text}}` shows how defaults can be used. In this case
;; if `text-type` is not specified `"text"` will be used.

;; Tweets to be processed
(def tweets
   ["How did everyone feel about the Climate Change question last night? Exactly."
    "Didn't catch the full #GOPdebate last night. Here are some of Scott's best lines in 90 seconds."
    "The biggest disappointment of my life came a year ago."])

(def sentiments (bg/complete-template sentimental
                  {:text-type "tweets" :tweets tweets}
                  ;; It is the same as in `review` example, just not using
                  ;; `system/openai` helper
                  {:gen [:llm/openai :provider/openai]}))

;; Generation results in the same order as `tweets`
^{::clerk/visibility {:code :hide}}
(clerk/html [:pre (-> sentiments second :gen)])
