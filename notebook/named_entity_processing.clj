^{:nextjournal.clerk/visibility {:code :fold}}
(ns named-entity-processing
  {:nextjournal.clerk/toc true}
  (:require
   [bosquet.llm.generator :refer [llm generate] :as g]
   [bosquet.llm.wkk :as wkk]
   [bosquet.template.selmer :as selmer]
   [clojure.string :as str]
   [helpers :as h]
   [bosquet.memory.simple-memory :as simple-memory]
   [bosquet.memory.retrieval :as r]
   [clojure.pprint :as p]))

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
                              "  Entities: {{example.entities|join:, }}"
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
  "Parses NER response and groups by observation dates, then by celestial bodies.
   Returns a vector of maps with nested observations per object.
   
   Example output:
   [{:date \"All month\"
     :observations [{:object \"Venus\"
                     :details [{:entity \"predawn\" :type :obstime}
                               {:entity \"east\" :type :skydirection}]}
                    {:object \"Mars\"
                     :details [{:entity \"west\" :type :skydirection}
                               {:entity \"midnight\" :type :obstime}]}]}]"
  [response-text]
  (let [pattern #"^\d+\.\s*(.+?)\s*\|\s*True\s*\|.*\(([^)]+)\).*$"
        
        entities (->> response-text
                      str/split-lines
                      (map str/trim)
                      (filter #(re-find #"^\d+\." %))
                      (keep (fn [line]
                              (when-let [matches (re-matches pattern line)]
                                {:entity (nth matches 1)
                                 :type   (-> matches (nth 2) keyword)}))))]

    (tap> {'entities entities
           'response response-text})
    
    ;; Process entities sequentially, building the nested structure
    (loop [remaining      entities
           current-date   nil
           current-object nil
           result         []]
      (if-let [item (first remaining)]
        (cond
          ;; New date
          (= :dateperiod (:type item))
          (recur (rest remaining)
                 (:entity item)
                 nil
                 (conj result {:date         (:entity item)
                               :observations []}))
          
          ;; New celestial body
          (= :celestialbody (:type item))
          (if current-date
            (recur (rest remaining)
                   current-date
                   (:entity item)
                   (update-in result
                              [(dec (count result)) :observations]
                              conj
                              {:object  (:entity item)
                               :details []}))
            ;; No date context, skip
            (recur (rest remaining) current-date current-object result))
          
          ;; Details for current object (obstime, skydirection, event, etc.)
          :else
          (if (and current-date current-object)
            (let [date-idx (dec (count result))
                  obs-idx  (dec (count (get-in result [date-idx :observations])))]
              (recur (rest remaining)
                     current-date
                     current-object
                     (update-in result
                                [date-idx :observations obs-idx :details]
                                conj
                                item)))
            (recur (rest remaining) current-date current-object result)))
        
        ;; Done
        result))))

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
           "CONSTRAINTS:"
           ""
           "When answering use precisly the same format of returning entities as given in the examples."
           "Never add enitities of the types that were not given in the definition."
           ""
           "ANSWER:"]]
   [:assistant (llm :gpt-5-mini wkk/output-format parse-ner-response wkk/var-name :ner)]])


(def astronomy-definition
  "An entity is a celestial body (celestialbody), constellation (constellation), astronomical event (event),
date or time period (dateperiod), observation time (obstime), or sky direction (skydirection).

Celestial bodies include planets, moons, stars, asteroids, comets with specific names. \"Moon\" when referring to Earth's moon
is an entity. \"Full Moon\" is an astronomical event.

Date/time periods include specific dates (Oct. 5, March 15), month references (All month, Later in the month), or specific times
(9:45 PM).

Observation times are specific periods of the night/day for making observations (before dawn, predawn, early evening, middle of
the night, after sunset).

Sky directions are cardinal directions where objects appear (east, west, overhead, southern sky).

Abstract concepts, adjectives describing appearance (bright, yellowish, reddish, very low, high up), and action verbs
(rises, getting lower, moving) are not entities.")

(defn ->entity-item
  [candidate is-entity? reasoning]
  {:entity-candidate candidate
   :is-entity (if is-entity? "True" "False")
   :reasoning reasoning})

(def astronomy-examples
  [{:text "All month: The bright star Sirius appears in the southern sky after sunset, rising higher before dawn.
On March 15-17: Mars and Venus will be visible in the west during early evening, near the constellation Orion. "
    :items [(->entity-item "All month" true "as it is a time period reference (dateperiod)")
            (->entity-item "Sirius" true "as it is a specific named star (celestialbody)")
            (->entity-item "bright" false "as it is an adjective describing appearance")
            (->entity-item "southern sky" true "as it is a sky direction (skydirection)")
            (->entity-item "after sunset" true "as it is an observation time period (obstime)")
            (->entity-item "rising higher" false "as it is a verb phrase describing motion")
            (->entity-item "before dawn" true "as it is an observation time period (obstime)")
            (->entity-item "March 15-17" true "as it is a specific date (dateperiod)")
            (->entity-item "Mars" true "as it is a specific planet (celestialbody)")
            (->entity-item "Venus" true "as it is a specific planet (celestialbody)")
            (->entity-item "visible" false "as it is an adjective describing state")
            (->entity-item "west" true "as it is a sky direction (skydirection)")
            (->entity-item "early evening" true "as it is an observation time period (obstime)")
            (->entity-item "Orion" true "as it is a specific constellation (constellation)")]}
   
   {:text "On March 29: Jupiter's Great Red Spot will be prominently visible overhead at midnight."
    :items [(->entity-item "Match 29" true "as it is a time period reference (dateperiod)")
            (->entity-item "Jupiter" true "as it is a specific planet (celestialbody)")
            (->entity-item "Great Red Spot" true "as it is a specific named feature on Jupiter (celestialbody)")
            (->entity-item "prominently visible" false "as it is a descriptive phrase about visibility")
            (->entity-item "overhead" true "as it is a sky direction (skydirection)")
            (->entity-item "midnight" true "as it is an observation time period (obstime)")]}])


;; The actual text to analyze
(def astronomy-text
  "All month: Very bright Jupiter rises in the middle of the night in the east, and is high overhead before dawn.
All month: Yellowish Saturn is up in the east in the early evening, and high up and moving west through most of the rest of the night.
Later in the month: Bright Mercury is low in the early evening west.
Oct. 5: Yellowish Saturn is near a nearly Full Moon.
Oct. 10: A very thin crescent Moon is very near super-bright Venus in the predawn east.
Oct. 14: Jupiter and the Moon rise near each other in the middle of the night and are high overhead before dawn. ")

(comment
  (def res (generate prompt
                     {:definition astronomy-definition
                      :examples   astronomy-examples
                      :text       astronomy-text}))
  (tap> res) 


  (def complex-resp "1. All month | True | as it is a time period reference (dateperiod)
2. Venus | True | as it is a specific planet (celestialbody)
3. predawn | True | as it is an observation time period (obstime)
4. east | True | as it is a sky direction (skydirection)
5. Jupiter | True | as it is a specific planet (celestialbody)
6. middle of the night | True | as it is an observation time period (obstime)
7. overhead | True | as it is a sky direction (skydirection)
8. Oct. 5 | True | as it is a specific date (dateperiod)
9. Saturn | True | as it is a specific planet (celestialbody)
10. Full Moon | True | as it is an astronomical event (event)")
  

  (clojure.pprint/pprint (parse-ner-response resp))
  #__)


;; # GPT-NER
;; https://arxiv.org/pdf/2304.10428
;; 

(defn parse-gpt-ner-response
  [response]
  (try
    (mapv second
          (re-seq #"@@(.*?)##" response))
    (catch Exception _
      (tap> {'error (str "Failed to parse: " response)})
      response)))


(def gpt-ner-prompt
  {:ner-task            ["I am an excellent linguist."
                         "The task is to label {{entity-type}} entities in the given sentence."
                         "Below are some examples."]
   :examples            ["{% for demo in demonstrations %}"
                         "Input: {{demo.input}}"
                         "Output: {{demo.output}}"
                         ""
                         "{% endfor %}"]
   :extractor           (llm :gpt-5-mini wkk/output-format parse-gpt-ner-response)
   :full-prompt         ["{{ner-task}}"
                         ""
                         "{{examples}}"
                         "Input: {{text}}"
                         "Output: {{extractor}}"]})

(def verification-prompt
  {:verification-prompt ["Verify that the given entities extracted from the text are of the {{entity-type}} entity type."
                         "Answer with YES or NO only in exact same order as provided questions Never add any reasoning or explantions."
                         "Answer NO only if you are absolutely positive that the mistake was made. Err on the side of YES."
                         "A comma separated YES/NO answers in the same order as provider words." 
                         "Text: {{text}}"
                         "Entities: {{entities|join:, }}"
                         "{{verifier}}"]
   :verifier            (llm :gpt-5-nano wkk/output-format (fn [resp]
                                                             (mapv (fn [w] (-> w str/trim (= "YES" w)))
                                                                   (str/split resp #","))))})

(defn only-confirmed-entities [entities validations]
  (reduce (fn [m [e v]] (if v (conj m e) m))
          []
          (map vector entities validations)))


(def entity-standartization-prompt
  {:prompt "TASK: Normalize entity mentions to their canonical form for frequency counting and entity resolution.

RULES FOR NORMALIZATION:
1. **Proper Nouns** (names of specific things): Preserve original capitalization
   - Example: 'Mars' stays 'Mars', 'Venus' stays 'Venus', 'Pleiades' stays 'Pleiades'
   
2. **Common Nouns** (generic categories): Convert to lowercase singular form UNLESS the entity is inherently plural
   - Example: 'Comet' and 'comets' both become 'comet'
   - Example: 'Meteor' and 'meteors' both become 'meteor'
   - Exception: 'Pleiades' stays 'Pleiades' (inherently plural proper noun)

3. **Mixed Cases** (proper noun + common descriptor): Keep only the proper noun part
   - Example: 'Halley's Comet' → normalize as 'Halley's Comet' (proper noun kept)
   - But if counting comets generically: 'comet'

4. **Acronyms/Abbreviations**: Preserve capitalization
   - Example: 'NASA' stays 'NASA', 'ESA' stays 'ESA'

EXAMPLES:
Input entities: ['Mars', 'mars', 'Venus', 'Comet', 'comets', 'Pleiades', 'meteor', 'Meteors', 'Andromeda Galaxy', 'galaxy', 'galaxies']
Output:
- Mars → Mars
- mars → Mars (capitalize proper noun)
- Venus → Venus
- Comet → comet (common noun, singular)
- comets → comet (common noun, singular)
- Pleiades → Pleiades (inherently plural proper noun)
- meteor → meteor (lowercase singular)
- Meteors → meteor (lowercase singular)
- Andromeda Galaxy → Andromeda Galaxy (proper noun preserved)
- galaxy → galaxy (common noun singular)
- galaxies → galaxy (common noun singular)

NOW NORMALIZE THE FOLLOWING ENTITIES:
Entity type: {{entity-type}}
Text: {{text}}
Entities found: {{entities}}

Return a Clojure EDN formated array of entities in cannonical form in the same sequence as you received it.

{{standartizer}}"
   :standartizer (llm :gpt-5-mini wkk/output-format :edn)})

;; Something to think about how to know which path to traverse with two heads
;; (merge gpt-ner-prompt verification-prompt) here we have two things full-prompt and verification-prompt
;; verification refers extractor but it will be properly filled in if full-prompt executes first

;; Memory to store examples for few-shot prompt

(def sm-rememberer (simple-memory/->remember))
(def sm-recaller (simple-memory/->cue-memory))

(sm-rememberer
 {}
 [{:input "Jupiter rises in the middle of the night in the east"
   :output "@@Jupiter## rises in the middle of the night in the east"}

  {:input "Yellowish Saturn is up in the east in the early evening"
   :output "Yellowish @@Saturn## is up in the east in the early evening"}

  {:input "Bright Mercury is low in the early evening west"
   :output "Bright @@Mercury## is low in the early evening west"}

  {:input "Venus appears in the predawn sky"
   :output "@@Venus## appears in the predawn sky"}

  {:input "Mars will be visible near the Moon tonight"
   :output "@@Mars## will be visible near the @@Moon## tonight"}

  {:input "The Orion constellation is prominent in winter"
   :output "The @@Orion## constellation is prominent in winter"}

  {:input "Neptune reaches opposition this month"
   :output "@@Neptune## reaches opposition this month"}

  {:input "Uranus is visible with binoculars in the eastern sky"
   :output "@@Uranus## is visible with binoculars in the eastern sky"}

  {:input "The Pleiades star cluster rises after midnight"
   :output "The @@Pleiades## star cluster rises after midnight"}])

(comment
  (def txt "Jupiter and the Moon rise near each other in the middle of the night. Comets are visible this month. The comet appears in May.")
  (def x
    (generate
     gpt-ner-prompt
     {:entity-type    "celestial body"
      :demonstrations (sm-recaller {r/memory-content :input} txt)
      :text           txt}))

  (def verif (generate
              verification-prompt
              {:entity-type "celestial body"
               :entities    (-> x g/completions :extractor)
               :text        txt}))

  (def entities (only-confirmed-entities (-> x g/completions :extractor)
                                         (-> verif g/completions :verifier)))

  (def normalized
    (generate
     entity-standartization-prompt
     {:entity-type "celestial body"
      :entities    entities
      :text        txt}))

  (def txt "Comets are visible this month. The comet appears in May.")
  (def x
    (generate
     gpt-ner-prompt
     {:entity-type    "celestial body"
      :demonstrations (sm-recaller {r/memory-content :input} txt)
      :text           txt}))

  (generate
   gpt-ner-prompt
   {:entity-type    "celestial body"
    :demonstrations (sm-recaller {r/memory-content :input}
                                 "China says Taiwan spoils atmosphere for talks")
    :text           "China says Taiwan spoils atmosphere for talks"})

  #__)
