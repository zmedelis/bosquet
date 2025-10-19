^{:nextjournal.clerk/visibility {:code :fold}}
(ns named-entity-processing
  {:nextjournal.clerk/toc true}
  (:require
   [bosquet.llm.generator :refer [llm]]
   [bosquet.llm.wkk :as wkk]
   [bosquet.template.selmer :as selmer]
   [clojure.string :as str]
   [helpers :as h]))

;; # Named Entity Recognition
;; 
;; Named Entity Recognition is a Natural Language Processing tehnique that
;; extracts substrings from the text that represent real-world objects: people,
;; organizations, locations, etc.
;;
;; Interest in NER
;;
;; https://arxiv.org/html/2401.10825v3/extracted/6075572/images/ner2024.png
;; (from https://arxiv.org/html/2401.10825v3#S3)
;;
;; Entity is described as something that has type, definition and examples.
;; Since most things language are vagualy defined examples of use are a
;; necessary part of the definition.
;;

(def EntityDefinition
  [:map
   [:entity-type [:enum "Person" "Location" "Organization" "Date"]]
   [:definition [:string {:min 5}]]
   [:examples [:set
               {:gen/min 1 :gen/max 3}
               [:map
                [:text [:string {:min 5}]]
                [:entities [:vector {:gen/min 1 :gen/max 3} [:string {:min 5}]]]]]]])

;; The task for entity detection system is two fold:
;; 1. Identity entity
;; 2. Classify entity

;; # Traditional approaches
;; 1. Spacy rule and ML
;; 2. GLiNER
;;
;; # LLM
;; Problem with LLM is that it they are for text generation not sequence labeling
;;
;; ## Why LLM?
;; Should we categorize the phrase
;; ‘Theory of General Relativity’ as an entity? A media company tasked with
;; extracting information from political articles might not designate physical
;; laws as a relevant class of entities but a scientific journal might. Given
;; the diversity of use cases and underlying documents that characterize
;; different deployment settings, we might hope ideally for a system to adapt to
;; new settings flexibly, requiring minimal labeled data, human effort, and
;; computational cost.
;;
;; Two problems remain
;; 1. LLMs are resource intensive
;; 2. Prompt sensitivity, no matter what this involves complex prompting and it is always problemantic
;; 

(defn llm-named-entity-extraction
  [text enity-definitions]
  (let [prompt {:goal (h/join "Detect the following entities:"
                              "{% for entity in entities %}"
                              "## {{entity.entity-type}} ##"
                              "{{entity.definition}}"
                              "{% for example in entity.examples %}"
                              "Example {{forloop.counter}}:"
                              "  Text: {{example.text}}"
                              "  Entities: {{example.entities|join: \", \"}}"
                              "{% endfor %}{% endfor %}"
                              ""
                              "TEXT:"
                              "{{text}}")}]
    (selmer/render (:goal prompt)
                   {:text     text
                    :entities enity-definitions})))

;; https://huggingface.co/spaces/urchade/gliner_multiv2.1
;; 
;; When the James Webb Space Telescope instrument ( JWST instrument ) looked back
;; in time to observe the Universe’s earliest moments, it presented astronomers
;; profession with something most peculiar: hundreds of ‘little red dots space
;; object ’ that inexplicably freckled the ancient cosmos.

;; The specks space object , named for their compact size in JWST instrument images
;; and their emission of long, ‘red’ wavelengths of light, initially baffled
;; astronomers profession . They seemed too condensed to be galaxies, yet didn’t
;; emit the right kind of light to be black holes space object . Researchers
;; profession quickly dubbed the dots, which JWST instrument first detected in
;; 2022, Universe breakers, because they contradicted standard thinking about the
;; features of the early Universe.
;;
;; instrument, profession, space object
;;
;; * 2ia prblema su JWST abr ir visu vardu, reikia sujunginėti


;; time, space object, position | kai pakeit i 'time, celestial object, position' tada
;; kiek mažiau problemų su 'Super bright Venus' gauni 'Venus' tačiau 'Reddish Mars' išlieka
;;
;; problemos
;; * Super bright Venus - noriu tik Venus, tiesa kai nuime 'nested ner' gauni ir SB Venus ir Venus
;; bet tada kuris teisingas. Galima būtų įvedinėti 'condition' or something kad atskirti 'super bright'
;; bet tada jau prarandam sprendimo paprastumą
;; * time problematiškas nes jį markina ir eilutės pradžioje t.y. kada galima matyti, ir eilutės gale 'weeks'
;; atskirti irgi problematiška nes padarius 'date, time' vėl problemos
;; * naudojant 'celestial object' pagauna 'Orionids' tačiau tik 'The Orionids' 'The Orionid meteor shower' nepagauta
;; * naudojant 'astronomical body' panašiai
;;
;;
;; All month: Super bright Venus is in the predawn east, getting lower as the weeks pass. 
;; All month: Very bright Jupiter rises in the middle of the night in the east, and is high overhead before dawn.
;; All month:  Yellowish Saturn is up in the east in the early evening, and high up and moving west through most of the
;; rest of the night. 
;; All month: Reddish Mars is very low in the evening west, getting even lower as the weeks pass.
;; Later in the month: Bright Mercury is low in the early evening west.
;; Oct. 5: Yellowish Saturn is near a nearly Full Moon.
;; Oct. 7: Full Moon
;; Oct. 14: Jupiter and the Moon rise near each other in the middle of the night and are high overhead before dawn. 

;; # PromptNER

(defn parse-ner-response
  "Parses NER response text and returns a vector of [entity type] tuples.
   Only includes entities marked as True.
   
   Example input line:
   '1. All month | True | as it is a time period reference (dateperiod)'
   
   Example output:
   [[\"All month\" \"dateperiod\"] [\"Venus\" \"celestialbody\"]]"
  [response-text]
  (tap> response-text)
  (let [ ;; ^\d+\.        - starts with number and period (e.g., "1.")
        ;; \s*           - optional whitespace
        ;; (.+?)         - capture entity name (non-greedy)
        ;; \s*\|\s*True  - pipe, "True", with optional spaces
        ;; .*            - anything in between
        ;; \(([^)]+)\)   - capture text inside parentheses (the type)
        pattern #"^\d+\.\s*(.+?)\s*\|\s*True\s*\|.*\(([^)]+)\).*$"]
    
    (->> response-text
         str/split-lines
         (map str/trim)
         (filter #(re-find #"^\d+\." %)) ;; keep only lines with text
         (keep (fn [line]
                 (when-let [matches (re-matches pattern line)]
                   (let [entity (nth matches 1)
                         type   (nth matches 2)]
                     [entity type]))))
         vec)))

(def prompt
  [[:user ["DEFINITION:"
           "{{definition}}"
           ""
           "{% for example in examples %}"
           "EXAMPLE {{forloop.counter}}:"
           "TEXT:"
           "{{example.text}}"
           ""
           "ANSWER:"
           "{% for item in example.items %}"
           "{{forloop.counter}}. {{item.entity-candidate}} | {{item.is-entity}} | {{item.reasoning}}"
           "{% endfor %}"
           "{% endfor %}"
           ""
           "Q: Given the text below, identify a list of possible entities and for each entry explain why it either is or is not an entity:"
           ""
           "TEXT:"
           "{{text}}"
           ""
           "When answering use precisly the same format of returning entities as given in the examples."
           ""
           "ANSWER:"]]
   [:assistant (llm :gpt-5-nano wkk/output-format parse-ner-response wkk/var-name :ner)]])

;; Updated Definition map - removed mission, added observation time
(def astronomy-definition
  "An entity is a celestial body (celestialbody), constellation (constellation), astronomical event (event), date or time period (dateperiod),
observation time (obstime), or sky direction (skydirection).

Celestial bodies include planets, moons, stars, asteroids, comets with specific names. \"Moon\" when referring to Earth's moon is an entity.
\"Full Moon\" is an astronomical event.

Date/time periods include specific dates (Oct. 5, March 15), month references (All month, Later in the month), or specific times (9:45 PM).

Observation times are specific periods of the night/day for making observations (before dawn, predawn, early evening, middle of the night, after sunset).

Sky directions are cardinal directions where objects appear (east, west, overhead, southern sky).

Abstract concepts, adjectives describing appearance (bright, yellowish, reddish, very low, high up), and action verbs (rises, getting lower, moving)
are not entities.")

(defn ->entity-item
  [candidate is-entity? reasoning]
  {:entity-candidate candidate
   :is-entity (if is-entity? "True" "False")
   :reasoning reasoning})

(def astronomy-examples
  [{:text "All month: The bright star Sirius appears in the southern sky after sunset, rising higher before dawn."
    :items [(->entity-item "All month" true "as it is a time period reference (dateperiod)")
            (->entity-item "Sirius" true "as it is a specific named star (celestialbody)")
            (->entity-item "bright" false "as it is an adjective describing appearance")
            (->entity-item "southern sky" true "as it is a sky direction (skydirection)")
            (->entity-item "after sunset" true "as it is an observation time period (obstime)")
            (->entity-item "rising higher" false "as it is a verb phrase describing motion")
            (->entity-item "before dawn" true "as it is an observation time period (obstime)")]}
   
   {:text "On March 15, Mars and Venus will be visible in the west during early evening, near the constellation Orion."
    :items [(->entity-item "March 15" true "as it is a specific date (dateperiod)")
            (->entity-item "Mars" true "as it is a specific planet (celestialbody)")
            (->entity-item "Venus" true "as it is a specific planet (celestialbody)")
            (->entity-item "visible" false "as it is an adjective describing state")
            (->entity-item "west" true "as it is a sky direction (skydirection)")
            (->entity-item "early evening" true "as it is an observation time period (obstime)")
            (->entity-item "Orion" true "as it is a specific constellation (constellation)")]}
   
   {:text "Later in the month, Jupiter's Great Red Spot will be prominently visible overhead at midnight."
    :items [(->entity-item "Later in the month" true "as it is a time period reference (dateperiod)")
            (->entity-item "Jupiter" true "as it is a specific planet (celestialbody)")
            (->entity-item "Great Red Spot" true "as it is a specific named feature on Jupiter (celestialbody)")
            (->entity-item "prominently visible" false "as it is a descriptive phrase about visibility")
            (->entity-item "overhead" true "as it is a sky direction (skydirection)")
            (->entity-item "midnight" true "as it is an observation time period (obstime)")]}])


;; The actual text to analyze
(def astronomy-text
  "All month: Super bright Venus is in the predawn east, getting lower as the weeks pass.
All month: Very bright Jupiter rises in the middle of the night in the east, and is high overhead before dawn.
All month: Yellowish Saturn is up in the east in the early evening, and high up and moving west through most of the rest of the night.
All month: Reddish Mars is very low in the evening west, getting even lower as the weeks pass.
Later in the month: Bright Mercury is low in the early evening west.
Oct. 5: Yellowish Saturn is near a nearly Full Moon.
Oct. 7: Full Moon.
Oct. 14: Jupiter and the Moon rise near each other in the middle of the night and are high overhead before dawn.")

;; Function call



(comment
  (def pner (prompt-ner))
  (def res (generate prompt
                     {:definition astronomy-definition
                      :examples   astronomy-examples
                      :text       astronomy-text}))
  #__)

