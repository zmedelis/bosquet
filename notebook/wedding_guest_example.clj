(ns wedding-guest-example
  (:require
   [nextjournal.clerk :as clerk]))

;; Here we will create "Thank you letters" for our wedding guests.
;; This will illustrate how to use generation over a database of
;; guests producing different letters based on the information
;; about the guest.
;;
;; This is what we know about the guests:

(def guests
  [{:name "Laura and Mike" :gift "Cheese board" :relationship "College friends of Diane" :hometown "San Francisco"}
   {:name "Uncle Steve and Aunt Sarah" :gift "China bowls" :relationship "Jack's aunt and uncle" :hometown "San Juan"}
   {:name "Maria and Joseph" :gift "Chainsaw" :relationship "Friend of Jack's parents" :hometown "Calgary"}
   {:name "Ava and Charlotte" :gift "Coat rack" :relationship "Diane's childhood friend and her wife" :hometown "Louisville"}
   {:name "Cory and Patricia" :gift "Kitchenaid Mixer" :relationship "Diane's parents's friends" :hometown "Valencia  Spain"}])

(clerk/table guests)

;; To generate individual letters we will instruct LLM with
;; a few examples illustrating what kind of things are to be said
;; given the information about the guest.

(def thank-you-letters-few-shot-exmples
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
Best, Jack and Diane"}])

(clerk/table thank-you-letters-few-shot-exmples)
