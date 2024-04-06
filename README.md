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

[Full project documentation](https://zmedelis.github.io/bosquet/) (WIP)

## Use

Secrets like keys are stored in `secrets.edn` file and local parameters are kept in `config.edn`. Make a copy of `config.edn.sample` and `config.edn.sample` files. Change as needed.

### CLI

Command line interface demo

[![CLI](https://img.youtube.com/vi/ywlNGiD9gCg/0.jpg)](https://www.youtube.com/watch?v=ywlNGiD9gCg)

Run the following command to get CLI options
```
clojure -M -m bosquet.cli
```

Set the default model with
```
clojure -M -m bosquet.cli llms set --service openai --temperature 0 --model gpt-4
```

Do not forget to set the API KEY for your service (change 'openai' to a different name if needed)
```
clojure -M -m bosquet.cli keys set openai
```

With that set, you can run generations:

```
clojure -M -m bosquet.cli "2+{{x}}="
```
Or using files
```
clojure -M -m bosquet.cli -p demo/play-writer-prompt.edn -d demo/play-writer-data.edn
```

### Prompt completion

Simple prompt completion can be done like this.

```colojure
(require '[bosquet.llm.generator :refer [generate llm]])

(generate "When I was 6 my sister was half my age. Now I’m 70 how old is my sister?")
=>
"When you were 6, your sister was half your age, which means she was 6 / 2 = 3 years old.\nSince then, there is a constant age difference of 3 years between you and your sister.\nNow that you are 70, your sister would be 70 - 6 = 64 years old."}
```


### Completion from the prompt map


```clojure
(require '[bosquet.llm :as llm])
(require '[bosquet.llm.generator :refer [generate llm]])

(generate
 llm/default-services
 {:question-answer "Question: {{question}}  Answer: {{answer}}"
  :answer          (llm :openai)
  :self-eval       ["Question: {{question}}"
                    "Answer: {{answer}}"
                    ""
                    "Is this a correct answer?"
                    "{{test}}"]
  :test            (llm :openai)}
 {:question "What is the distance from Moon to Io?"})
=>

{:question-answer
 "Question: What is the distance from Moon to Io?  Answer:",
 :answer
 "The distance from the Moon to Io varies, as both are orbiting different bodies. On average, the distance between the Moon and Io is approximately 760,000 kilometers (470,000 miles). However, this distance can change due to the elliptical nature of their orbits.",
 :self-eval
 "Question: What is the distance from Moon to Io?\nAnswer: The distance from the Moon to Io varies, as both are orbiting different bodies. On average, the distance between the Moon and Io is approximately 760,000 kilometers (470,000 miles). However, this distance can change due to the elliptical nature of their orbits.\n\nIs this a correct answer?",
 :test
 "No, the answer provided is incorrect. The Moon is Earth's natural satellite, while Io is one of Jupiter's moons. Therefore, the distance between the Moon and Io can vary significantly depending on their relative positions in their respective orbits around Earth and Jupiter."}

```

### Chat

``` clojure
(require '[bosquet.llm.wkk :as wkk])
(generate
    [[:system "You are an amazing writer."]
     [:user ["Write a synopsis for the play:"
             "Title: {{title}}"
             "Genre: {{genre}}"
             "Synopsis:"]]
     [:assistant (llm wkk/openai
                    wkk/model-params {:temperature 0.8 :max-tokens 120}
                    wkk/var-name :synopsis)]
     [:user "Now write a critique of the above synopsis:"]
     [:assistant (llm wkk/openai
                    wkk/model-params {:temperature 0.2 :max-tokens 120}
                    wkk/var-name     :critique)]]
    {:title "Mr. X"
    :genre "Sci-Fi"})
=>

#:bosquet{:conversation
          [[:system "You are an amazing writer."]
           [:user
            "Write a synopsis for the play:\nTitle: Mr. X\nGenre: Sci-Fi\nSynopsis:"]
           [:assistant "In a futuristic world where technology ..."]
           [:user "Now write a critique of the above synopsis:"]
           [:assistant
            "The synopsis for the play \"Mr. X\" presents an intriguing premise ..."]],
          :completions
          {:synopsis
           "In a futuristic world where technology has ...",
           :critique
           "The synopsis for the play \"Mr. X\" presents an intriguing premise set ..."}}
```

Generation returns `:bosquet/conversation` listing full chat with generated parts filled in, and `:bosquet/completions` containing only generated data
