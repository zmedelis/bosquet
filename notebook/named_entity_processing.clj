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

(defn llm-namaed-entity-extraction
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
