# Bosquet

## Getting Started

The simplest use case showing some of the basic Bouquet functionality is using linked prompt templates for text generation.

Let's say we want AI to generate two texts:
1. a synopsis of the play from `title` and `genre` inputs
1. a synopsis review.

Something like this:

> Title: City of Shadows
> Genre: Crime
> Synopsis:
> In the gritty underbelly of City of Shadows, corruption and crime reign supreme. The play follows the interconnected lives of several individuals, revealing the intricate web of deceit and darkness that engulfs the city. Detective Mark Johnson ...
> 
> Review:
> Riveting Examination of Corruption and Justice
> Rating: ★★★★☆ 
> ...

To do this, let's define a template with a prompt and slots for data insertion. *Bosquet* uses a fork of [Selmer](https://github.com/zmedelis/Selmer) templating library for prompt definitions. *Selmer* provides rich templating language, but for now, all that is needed is its `{{DATA}}` syntax to mark spots in the text for data injection.

Linked templates are defined in a map, where the map key is a variable name that can be used to reference templates from each other.

```clojure
(def template
  {:synopsis  
  "You are a playwright. Given the play's title and it's genre
it is your job to write synopsis for that play.

Title: {{title}}
Genre: {{genre}}

{% gen var-name=play %}"

   :critique
   "You are a play critic from the Moon City Times.
Given the synopsis of play, it is your job to write a review for that play.

Play Synopsis:
{{play}}

Review from a New York Times play critic of the above play:
{% gen var-name=review %}"})
```

Things to note:
* `gen` template tag marks the place where AI-generated text will be inserted; the `gen` tag uses the text above as its generation content
* `var-name` will be the key in returned generation data under which generation result will be available
* `{{title}}` (and other slots in that form) is where supplied inputs will be injected

*Bosquet* will be invoking *OpenAI API* thus make sure that `OPENAI_API_KEY` is present as the environment variable. 

With the prerequisite data set, let's run the generation.

```clojure
(require '[bosquet.llm.generator :as g])

(g/generate template {:title "City of Shadows" :genre "crime"}) 
=>
{:title    "City of Shadows"
 :genre    "crime"
 :synopsis "You are a playwright. Given the play's title and it's genre\nit is your job to write synopsis for that play.\n\nTitle: City of Shadows..."
 :play     "Synopsis:\nIn the gritty underbelly of City of Shadows, corruption and crime reign supreme. The play follows the interconnected...",
 :critique "You are a play critic from the Moon City Times...",
 :review   "Title: City of Shadows: A Riveting Examination of Corruption and Justice\n\nRating: ★★★★☆\n\nIn the City of Shadows, where deceit and crime intertwine..."}
```

The output contains the map with:
* input data under `title` and `genre`
* complete template filling results under `synopsis ` and `critique` including template text and AI generation result
* AI generation only results under `var-name` keys
