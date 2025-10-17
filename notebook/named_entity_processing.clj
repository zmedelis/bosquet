^{:nextjournal.clerk/visibility {:code :fold}}
(ns named-entity-processing
  {:nextjournal.clerk/toc true}
  (:require
   [bosquet.template.selmer :as selmer]
   [helpers :as h]))

(def EntityDefinition
  [:map
   [:entity-type [:enum "Person" "Location" "Organization" "Date"]]
   [:definition [:string {:min 5}]]
   [:examples [:set
               {:gen/min 1 :gen/max 3}
               [:map
                [:text [:string {:min 5}]]
                [:entities [:vector {:gen/min 1 :gen/max 3} [:string {:min 5}]]]]]]])

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
;; All month:  Yellowish Saturn is up in the east in the early evening, and high up and moving west through most of the rest of the night. 
;; All month: Reddish Mars is very low in the evening west, getting even lower as the weeks pass.
;; Later in the month: Bright Mercury is low in the early evening west.
;; Oct. 5: Yellowish Saturn is near a nearly Full Moon.
;; Oct. 7: Full Moon
;; Oct. 14: Jupiter and the Moon rise near each other in the middle of the night and are high overhead before dawn. 
