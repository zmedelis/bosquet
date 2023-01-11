(ns bosquet.examples
  (:require
   [bosquet.generator :as generator]))

(def play
  {:review
   "You are a play critic from the New York Times.
Given the synopsis of play, it is your job to write a review for that play.

Play Synopsis:
{{synopsis/completion}}
Review from a New York Times play critic of the above play:
((bosquet.openai/complete))"

   :synopsis
   "You are a playwright. Given the title of play and the desired style,
it is your job to write a synopsis for that title.

Title: {{title}}
Style: {{style}}
Playwright: This is a synopsis for the above play:
((bosquet.openai/complete))"})

(defn play-review []
  (generator/complete
    play
    {:title "The big play"
     :style "comedy"}
    #_{:model "text-davinci-003"}))

(comment
  (play-review))
