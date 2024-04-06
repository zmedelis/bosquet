^{:nextjournal.clerk/visibility {:code :hide}}
(ns wedding-guest-example
  (:require
   [bosquet.llm.generator :refer [llm generate]]
   [nextjournal.clerk :as clerk]))

;; # Generating thank you letters
;;
;; There was a wedding with lots of guests. They traveled far and wide to attend
;; it, brough gifts and all. We want to generate thank you letters for each
;; guest. The letters should be personalized and note how far they traveled, and
;; how great is their gift. Letters ought to be written in a tone appropriate for
;; the type of the relationship between the guest and the couple.
;;
;; ## The guests
;;
;; The list of guests is taken from the couple's spreadsheet they used to note
;; who came to their wedding. There were 178 guests in total, hence the need for
;; automation. This sample shows typical types of guests' relationships with the
;; couple, the geographical spread of visitors, and the gifts they brought.
;; Those types of data will have to be reflected in personalized letters.

^{::clerk/visibility {:code :fold}}
(def guests
  [{:name "Laura and Mike" :gift "Cheese board" :relationship "College friends of Diane" :hometown "San Francisco"}
   {:name "Uncle Steve and Aunt Sarah" :gift "China bowls" :relationship "Jack's aunt and uncle" :hometown "San Juan"}
   {:name "Maria and Joseph" :gift "Chainsaw" :relationship "Friend of Jack's parents" :hometown "Calgary"}
   {:name "Ava and Charlotte" :gift "Coat rack" :relationship "Diane's childhood friend and her wife" :hometown "Louisville"}
   {:name "Cory and Patricia" :gift "Kitchenaid Mixer" :relationship "Diane's parents's friends" :hometown "Valencia  Spain"}])

^{::clerk/visibility {:code :hide}}
(clerk/table guests)

;; ## The Instructions
;;
;; Large Language Model needs to learn how to write thank you letters. LLMs need
;; only [a few examples](https://www.promptingguide.ai/techniques/fewshot) and
;; instructions on how to write a letter. The examples need to illustrate how
;; different relationship types, gifts, and distances traveled impact the tone
;; and content of the letter.
;;
;; The instructions are composed of three parts:
;; * data about the guest
;; * step-by-step guidance on how to write the letter
;; * and the example letter itself.

;; ### Data
;; We take a guest representing a certain group of those who attended the wedding:

^{::clerk/visibility {:code :hide}}
(clerk/html [:ul.viewer.viewer-code.w-full.max-w-wide
             [:li [:em "Name"] " - Nancy"]
             [:li [:em "Relationship"] " - Friend of Jack's parents"]
             [:li [:em "Hometown"] " - New Jersy"]
             [:li [:em "Gift"] " - Set of mixing bowls"]])

;; ### Step-by-step guidance
;; We need to provide step-by-step instructions to LLM on how to write a thank you letter. In _Nancy's_ case
;; those instructions would be:

^{::clerk/visibility {:code :hide}}
(clerk/html [:ul.viewer.viewer-code.w-full.max-w-wide
             [:li "Nancy is a friend of Jack's parents, so the tone should be more formal and reference how important she is to Jack's parents."]
             [:li "New Jersey and Puerto Rico are a long distance apart, so we should thank her for making the trip."]
             [:li "Mixing bowls are a nice gift because they can be used for baking."]])

;; ### Example letter
;; Finally, the last component to finish teaching LLM to write thank you letters
;; is to give it an actual sample letter given the above data about the guests
;; and instructions on how to write a letter.

^{::clerk/visibility {:code :hide}}
(clerk/html [:div.whitespace-pre-line.max-w-wide.bg-white.p-4.text-slate-500.text-sm
             "Dear Nancy,

Thank you so much for attending our wedding in Puerto Rico and for the lovely gift of mixing bowls.
We love to bake and will think of you every time we use them. It was so kind of you to make the trip
all the way from New Jersey to celebrate with us. We are truly grateful to have such supportive and
close friends like you in our lives.

We will of course let you know when we're next in New Jersey, and hope to see you soon!

Best, Jack and Diane"])

;; ### Full set of instructions
;;
;; The above process needs to be repeated for each type of the guest. There is no need to povide lots of examples
;; a few representing most typical cases will do. LLM will take care of the rest.
;;

^{::clerk/visibility {:code :fold}}
(def thank-you-letters-few-shot-exmples
  {:examples
   [{:guest
     "Name: Nancy, Relationship: Friend of Jack's parents, Gift: Set of mixing bowls, Hometown: New Jersey"
     :step
     "- Nancy is a friend of Jack's parents, so the tone should be more formal and reference how important she is to Jack's parents.
- New Jersey and Puerto Rico are a long distance apart, so we should thank her for making the trip.
- Mixing bowls are a nice gift because they can be used for baking."
     :note
     "Dear Nancy,
Thank you so much for attending our wedding in Puerto Rico and for the lovely gift of mixing bowls.
We love to bake and will think of you every time we use them. It was so kind of you to make the trip all the way from New Jersey to celebrate with us. We are truly grateful to have such supportive and close friends like you in our lives. We will of course let you know when we're next in New Jersey, and hope to see you soon!
Best, Jack and Diane"}
    {:guest
     "Name: Joe Lewis,Relationship: Friend of Jack's from college., Gift: Bar set, Hometown: Puerto Rico"
     :step
     "- Joe is a friend of Jack's from college, so the tone should be more casual and friendly.
- Joe lives close to the wedding, so we can tell him how much we enjoyed having him there.
- A bar set is a nice gift because it can be used for entertaining."
     :note
     "Dear Joe, Thank you so much for coming to our wedding and for the fabulous bar set.
We love entertaining and will think of you every time we use it. It was great to see you and catch up. We are so lucky to have such supportive and close friends like you in our lives.
Best, Jack and Diane"}
    {:guest
     "Name: Lane Michaels, Relationship: Diane's cousin, Gift: Crystal lamp, Hometown: Miami"
     :step
     "- Lane is Diane's cousin, so the tone should be more family oriented.
- Miami is a close distance to Puerto Rico, so we should thank her for making the trip.
- A crystal lamp is a nice gift because it can be used as a decoration."
     :note
     "Dear Lane,
Thank you so much for coming to our wedding and for the beautiful crystal lamp.
We love it and it will look perfect in our new home. We are so grateful to have such supportive and close family like you in our lives.
Hopefully we'll be coming through Miami sometime soon. Would love to see you!
Best, Jack and Diane"}]})


^{::clerk/visibility {:code :hide}}

(clerk/table
  (:examples thank-you-letters-few-shot-exmples))


;; ## The Prompt
;;
;; The prompt is a short description of the task that LLM needs to perform. There is an excellent [DAIR.AI introduction to Prompt Engineering](https://github.com/dair-ai/Prompt-Engineering-Guide/blob/main/guides/prompts-intro.md).
;; With [Bosquet](https://github.com/BrewLLM/bosquet) you can compose prompts out of individual components, while [Selmer]() templating engine will provide necessary
;; tools merge guest data and instructions into the prompt.

;; Prompt components defining the generation.
;; * `context` - sets the theme of the letter, note that it can be swapped with the rest unchanged
;; * `few-shot-examples` - only a part iterating over the letter writting example instructions
;; * `letter` - the part where all the previous components and data are merged together to send for generation
;; * `letter-generation` - this one is different, it is defined in `llm-generate` and specfies which key will hold only the generated text
(def letter-writter
  {:context           ["Jack and Diane just had their wedding in Puerto Rico "
                       "and it is time to write thank you cards. For each"
                       "guest, write a thoughtful, sincere, and personalized"
                       "thank you note using the information provided below."]
   :few-shot-examples ["{% for example in examples %}"
                       "Guest Information:"
                       "{{example.guest}}"
                       "First, let's think step by step:"
                       "{{example.step}}"
                       "Next, let's draft the letter:"
                       "{{example.note}}"
                       "{% endfor %}"]
   :instructions      ["{{context}}"
                       "{{few-shot-examples}}"
                       "Guest Information:"
                       "Name: {{name}}"
                       "Relationship: {{relationship}}"
                       "Gift: {{gift}}"
                       "Hometown: {{hometown}}"
                       "First, let's think step by step:"
                       "{{letter}}"]
   :letter            (llm :gpt-3.5-turbo)})

;; ## Generate letters
;;
;; With the promts defined we can now generate the letters.

(def letter-generator (partial generate letter-writter))

^{::clerk/visibility {:result :hide}}
(def letters
  (pmap
   (fn [guest]
     (letter-generator
      (merge guest thank-you-letters-few-shot-exmples)))
   guests))

;; #### Generated letters

;; First two examples of generated text

^{::clerk/visibility {:code :hide}}
(clerk/row
  (clerk/html [:div.whitespace-pre-line.max-w-md.bg-white.p-4.text-slate-500.text-sm
               (-> letters first :bosquet/completions :letter)])

  (clerk/html [:div.whitespace-pre-line.max-w-md.bg-white.p-4.text-slate-500.text-sm
               (-> letters second :bosquet/completions :letter)]))

;; Full generation data

^{::clerk/visibility {:code :hide}}
(clerk/table (map :bosquet/completions letters))
