^{:nextjournal.clerk/visibility {:code :fold}}
(ns named-entity-processing
  {:nextjournal.clerk/toc                  true
   :nextjournal.clerk/auto-expand-results? true}
  (:require
   [bosquet.llm.generator :refer [generate llm] :as g]
   [bosquet.llm.wkk :as wkk]
   [bosquet.memory.retrieval :as r]
   [bosquet.memory.simple-memory :as simple-memory]
   [clojure.string :as str]
   [nextjournal.clerk :as clerk]))

;; # Named Entity Recognition
;;
;; ![NER Interest 2024](notebook/assets/ner_example.png)
;;
;; **Named Entity Recognition (NER)** is a Natural Language Processing technique that
;; extracts substrings from text representing real-world objects: people, organizations,
;; locations, dates, and other entity types of interest.
;;
;; ## Why NER Matters?

;; * **Real-world Information Extraction** - Automatically extract key facts from huge text volumes. *Example: Scanning customer reviews to identify which specific products (Mustang, Explorer) are mentioned most frequently.*
;; * **Foundation for Advanced NLP Tasks** - Essential building block for complex AI systems. *Example: A virtual assistant distinguishing between "I need to call Amazon" (company) vs "I'm traveling to the Amazon" (rainforest).*
;; * **Business Intelligence and Decision Making** - Monitor domains of interest in real-time. *Example: Alerting whenever "country leadership" and "resignation" appear together in news articles or social media.*
;; * **Healthcare and Scientific Discovery** - Help with medical research or improve patient care. *Example: Mining clinical trials to identify which patients with "diabetes" (disease) also took "Aspirin" (drug) to detect unexpected treatment patterns.*
;; * **Enhanced Search and Recommendation Systems** - Understand user intent beyond keywords. *Example: recommending jaguar wildlife documentaries vs Jaguar car reviews.*
;;
;; According to research trends, interest in NER has grown significantly in recent years.
;; ![NER Interest 2024](notebook/assets/ner2024.png)
;; *(Taken from: [arXiv:2401.10825v3](https://arxiv.org/html/2401.10825v3#S3))*
;;
;; ## The NER Task
;;
;; Entity detection systems must accomplish two complementary goals:
;;
;; 1. **Identify entities** - Locate spans of text that represent entities
;; 2. **Classify entities** - Assign appropriate types to the identified spans
;;
;; Both tasks are challenging because language is inherently ambiguous and context-dependent. 
;; Consider "Apple" - is it a fruit, a company, or a record label? Or "New York" vs "New York City" - 
;; where should entity boundaries be drawn? Context and domain knowledge determine the answers.
;; 
;; # Non LLM Approaches
;; 
;; LLMs are not necessary for NER. There are proven, well-working tools and techniques:
;;
;; 1. **Transformer/CNN-based models** - [Spacy](https://demos.explosion.ai/displacy-ent) - uses CNNs with and Transformers
;; 2. **Rule-based matching** - [Spacy EntityRuler](https://demos.explosion.ai/matcher) - pattern-based extraction
;; 3. **Bidirectional encoder** - [GLiNER](https://huggingface.co/spaces/urchade/gliner_multiv2.1) - generalist lightweight model
;;
;; *Text for testing*
;; ```text
;; All month: Super bright Venus is in the predawn east, getting lower as the weeks pass.
;; All month: Very bright Jupiter rises in the middle of the night in the east, and is high overhead before dawn.
;; All month: Yellowish Saturn is up in the east in the early evening, and high up and moving west through most of the rest of the night.
;; All month: Reddish Mars is very low in the evening west, getting even lower as the weeks pass.
;; Later in the month: Bright Mercury is low in the early evening west.
;; Oct. 5: Yellowish Saturn is near a nearly Full Moon.
;; Oct. 14: Jupiter and the Moon rise near each other in the middle of the night and are high overhead before dawn.
;; Oct. 15-20: Jupiter and the Moon rise near each other in the middle of the night. Comets are visible as well. The comet appears on 20th.
;; ```
;;
;; ## Limitations of Traditional Methods
;;
;; Traditional NER tools work well for trained entities (PERSON, LOCATION, ORGANIZATION)
;; but struggle with:
;;
;; - **Domain-specific low resource entities** - Require retraining for astronomy, medical, or legal terms
;; - **Nested entities** - "Super bright Venus" contains modifier + entity
;; - **Ambiguous boundaries** - Extract "Venus" or "Super bright Venus"?
;; - **Context-dependent types** - "Apple" as fruit vs. company, "Full Moon" as event vs. celestial body
;; - **Zero-shot capability** - Cannot recognize
;;
;; Those issues apply to LLMs but their reasoning capabilities help to address those.
;;
;; # LLM-Based NER
;;
;; Large Language Models offer a fundamentally different approach to NER with both benefits but also their own shortcommings.
;;
;; ## The Fundamental Mismatch
;;
;; LLMs are designed for **text generation**, not **sequence labeling**. Traditional NER treats 
;; entity recognition as token classification, but LLMs work by predicting the next token in a sequence.
;;
;; ## Why Use LLMs Anyway?
;;
;; Consider: Should "Theory of General Relativity" be extracted as an entity?
;;
;; - **Media company** analyzing political news → **No**
;; - **Scientific journal** indexing research → **Yes**
;;
;; The answer depends entirely on your domain and use case. This is where LLMs excel: **flexible, 
;; zero or few shot adaptation to domain-specific requirements** without retraining or labeled data.
;;
;; ### Key Advantages
;;
;; 1. **Zero/Few-shot capability** - Define new entity types through natural language instructions
;; 2. **Rich contextual understanding** - Handles ambiguity better ("Apple" in tech vs. food context)
;; 3. **Beyond extraction** - Normalization ("Mr. Medelis" → "Zygimantas Medelis"), entity resolution, 
;;    and relationship extraction in one step
;; 4. **Few-shot learning** - Provide examples directly in the prompt without retraining
;;
;; ### Key Challenges
;;
;; 1. **Computational cost** - 10-100x slower and more expensive than traditional methods
;; 2. **Prompt engineering** - Performance highly sensitive to instruction phrasing
;; 3. **Consistency** - May produce different outputs for identical inputs
;; 4. **Output formatting** - Requires careful prompt design to get structured results
;;
;; ## The Bottom Line
;;
;; LLMs excel when you need flexibility, domain adaptation, or semantic understanding. Traditional 
;; methods win on speed, cost, and consistency. Let's explore two practical LLM approaches.
;;
;; # Approach 1: PromptNER
;;
;; PromptNER ([arXiv:2305.15444](https://arxiv.org/abs/2305.15444)) demonstrates that well-structured 
;; prompts with few-shot examples can achieve competitive NER performance without fine-tuning.
;; 
;; ![PromptNER Architecture](notebook/assets/prompt_ner.png)
;;
;; ### PromptNER Implementation with Bosquet

;; #### Function to parse results
{:nextjournal.clerk/visibility {:code :fold}}
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

;; #### Prompt
{:nextjournal.clerk/visibility {:code :show}}
(def prompt-ner-prompt
  [[:user ["DEFINITION:" ;; 1. Entity type definition and criteria
           "{{definition}}"
           ""
           "{% for example in examples %}" ;; 2. Few-shot examples (looping through provided examples)
           "EXAMPLE {{forloop.counter}}:"
           "TEXT:"
           "{{example.text}}"
           ""
           "ANSWER:"
           "{% for item in example.items %}" ;; 3. Example outputs showing expected format
           "{{forloop.counter}}. {{item.entity-candidate}} | {{item.is-entity}} | {{item.reasoning}}"
           "{% endfor %}"
           "{% endfor %}"
           ""
           "Q: Given the text below, identify a list of possible entities and for each entry explain why it either is or is not an entity:"
           ""
           "TEXT:"
           "{{text}}" ;; 4. The actual text to process
           ""
           "CONSTRAINTS:" ;; 5. Explicit constraints to improve consistency
           ""
           "When answering use precisly the same format of returning entities as given in the examples."
           "Never add enitities of the types that were not given in the definition."
           ""
           "ANSWER:"]]
   [:assistant (llm :gpt-5-mini ;; 6. LLM call with output parsing
                    wkk/output-format parse-ner-response wkk/var-name :ner)]])

;; **Key Components:**
;;
;; 1. **Template variables** - `{{definition}}`, `{{text}}` are filled at runtime
;; 2. **Selmer loops** - `{% for example in examples %}` iterates through few-shot examples
;; 3. **Structured format** - Each example shows: candidate | is-entity (yes/no) | reasoning
;; 4. **Explicit constraints** - Reduces hallucination and ensures format consistency
;; 5. **Bosquet integration** - `:assistant` role with LLM call and custom parser (`parse-ner-response`)
;;
;; ## PromptNER Example: Astronomy Domain
;;
;; Let's apply PromptNER to extract entities from astronomical observation schedules—a 
;; domain-specific use case where traditional NER tools would require extensive retraining.

;; ### Entity Definition
;;
;; We define 6 entity types specific to astronomy observations:

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

;; **Key aspects:**
;; - **Specific types** - `celestialbody`, `dateperiod`, `obstime`, `skydirection`
;; - **Clear boundaries** - "Moon" vs "Full Moon" (body vs event)
;; - **Explicit exclusions** - Adjectives ("bright", "yellowish") and verbs ("rises", "moving") are NOT entities
;; 
;; ### Few-Shot Examples
;;
;; PromptNER learns from examples showing both positive and negative cases:

{:nextjournal.clerk/visibility {:code :fold}}
(defn ->entity-item
  [candidate is-entity? reasoning]
  {:entity-candidate candidate
   :is-entity (if is-entity? "True" "False")
   :reasoning reasoning})

{:nextjournal.clerk/visibility {:code :show}}
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

;; Each example demonstrates:
;; 1. **Entity candidates** - All possible entities in the text
;; 2. **Binary classification** - True/False for each candidate
;; 3. **Reasoning** - Why it is or isn't an entity of a specific type
;;
;; This format teaches the LLM to think step-by-step and provide justification.

;; ### Input Text
;; The actual text to analyze
;; 
(def astronomy-text
  "All month: Very bright Jupiter rises in the middle of the night in the east, and is high overhead before dawn.
All month: Yellowish Saturn is up in the east in the early evening, and high up and moving west through most of the rest of the night.
Later in the month: Bright Mercury is low in the early evening west.
Oct. 5: Yellowish Saturn is near a nearly Full Moon.
Oct. 10: A very thin crescent Moon is very near super-bright Venus in the predawn east.
Oct. 14: Jupiter and the Moon rise near each other in the middle of the night and are high overhead before dawn. ")


;; ## Running PromptNER
;;
;; Let's apply the PromptNER approach to our astronomy text. The prompt includes:
;; - Clear entity type definitions
;; - Few-shot examples showing reasoning
;; - Explicit instructions on what to extract

^{:nextjournal.clerk/visibility {:result :hide}}
(def prompt-ner-result
  (generate prompt-ner-prompt
            {:definition astronomy-definition
             :examples   astronomy-examples
             :text       astronomy-text}))

;; ### Input Text

^{:nextjournal.clerk/visibility {:code :hide}}
(clerk/html
  [:div.block.p-6.bg-white.border.border-gray-200.rounded-lg.shadow.dark:bg-gray-800.dark:border-gray-700
   [:div.font-mono.text-sm.whitespace-pre-wrap astronomy-text]])

;; ### Extracted Entities
;;
;; The structured output groups entities by date and celestial body, creating a hierarchical
;; representation of astronomical observations (this uses output from `parse-ner-response`).

^{:nextjournal.clerk/visibility {:code :hide}}
(clerk/html
  [:div.font-mono
   (for [observation (-> prompt-ner-result g/completions :ner)]
     [:div.block.p-4.mb-4.bg-white.border.border-gray-200.rounded-lg.shadow.dark:bg-gray-800.dark:border-gray-700
      [:div.font-bold.mb-2 (str "📅 " (:date observation))]
      (for [obs (:observations observation)]
        [:div.ml-4.mb-2
         [:div.text-blue-600 (str "🪐 " (:object obs))]
         [:ul.ml-6.list-disc
          (for [detail (:details obs)]
            [:li (str (:entity detail) " (" (name (:type detail)) ")")])]])])])

;; # Approach 2: GPT-NER
;;
;; GPT-NER ([arXiv:2304.10428](https://arxiv.org/pdf/2304.10428)) takes a different approach
;; using delimiter-based annotation and a two-stage verification process.
;;
;; ![GPT-NER Extraction](notebook/assets/gpt_ner.png)
;;
;; ## How GPT-NER Works
;;
;; The method uses a simple but effective pattern:
;; 1. **Extraction**: Mark entities with delimiters `@@entity##`
;; 2. **Verification**: Use a second LLM call to validate extractions
;;
;; ![GPT-NER Validation](notebook/assets/gpt_ner_validation.png)
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



;; Something to think about how to know which path to traverse with two heads
;; (merge gpt-ner-prompt verification-prompt) here we have two things full-prompt and verification-prompt
;; verification refers extractor but it will be properly filled in if full-prompt executes first

;; ## Setting Up GPT-NER Memory
;;
;; GPT-NER uses few-shot examples to guide the extraction. We'll store examples in memory
;; and retrieve the most relevant ones for each query.

(def sm-rememberer (simple-memory/->remember))
(def sm-recaller (simple-memory/->cue-memory))

^{:nextjournal.clerk/visibility {:result :hide}}
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

;; These examples demonstrate the `@@entity##` annotation pattern that the model should follow.

;; ## GPT-NER in Action
;;
;; First, let's verify the model doesn't hallucinate entities in unrelated text.

(def non-astronomy-text "China says Taiwan spoils atmosphere for talks")

^{:nextjournal.clerk/visibility {:result :hide}}
(def negative-test
  (generate
   gpt-ner-prompt
   {:entity-type    "celestial body"
    :demonstrations (sm-recaller {r/memory-content :input} non-astronomy-text)
    :text           non-astronomy-text}))

^{:nextjournal.clerk/visibility {:code :hide}}
(clerk/html
  [:div.block.p-6.bg-white.border.border-gray-200.rounded-lg.shadow.dark:bg-gray-800.dark:border-gray-700
   [:div.mb-2 [:strong "Input: "] [:span.font-mono non-astronomy-text]]
   [:div [:strong "Extracted: "]
    [:span.font-mono (str (g/completions negative-test :extractor))]]])

;; Good! No false positives. Now let's test on actual astronomy text.

(def gpt-ner-test-text
  "Jupiter and the Moon rise near each other in the middle of the night. Comets are visible this month. The comet appears in May.")

^{:nextjournal.clerk/visibility {:result :hide}}
(def gpt-ner-extraction
  (generate
   gpt-ner-prompt
   {:entity-type    "celestial body"
    :demonstrations (sm-recaller {r/memory-content :input} gpt-ner-test-text)
    :text           gpt-ner-test-text}))

;; ### Extraction Results

^{:nextjournal.clerk/visibility {:code :hide}}
(clerk/html
  [:div.block.p-6.bg-white.border.border-gray-200.rounded-lg.shadow.dark:bg-gray-800.dark:border-gray-700
   [:div.mb-4 [:strong "Input Text:"]]
   [:div.font-mono.mb-4.text-sm gpt-ner-test-text]
   [:div.mb-2 [:strong "Extracted Entities:"]]
   [:ul.list-disc.ml-6
    (for [entity (-> gpt-ner-extraction g/completions :extractor)]
      [:li.font-mono entity])]])

;; ### Two-Stage Verification
;;
;; GPT-NER's second stage validates the extractions, reducing false positives.

^{:nextjournal.clerk/visibility {:result :hide}}
(def gpt-ner-verification
  (generate
   verification-prompt
   {:entity-type "celestial body"
    :entities    (-> gpt-ner-extraction g/completions :extractor)
    :text        gpt-ner-test-text}))

^{:nextjournal.clerk/visibility {:code :hide}}
(clerk/html
  [:div.block.p-6.bg-white.border.border-gray-200.rounded-lg.shadow.dark:bg-gray-800.dark:border-gray-700
   [:div.mb-2 [:strong "Verification Results:"]]
   [:table.min-w-full.divide-y.divide-gray-200
    [:thead
     [:tr
      [:th.px-4.py-2.text-left "Entity"]
      [:th.px-4.py-2.text-left "Verified"]]]
    [:tbody
     (map (fn [entity verified]
            [:tr
             [:td.px-4.py-2.font-mono entity]
             [:td.px-4.py-2 (if verified "✅ YES" "❌ NO")]])
          (-> gpt-ner-extraction g/completions :extractor)
          (-> gpt-ner-verification g/completions :verifier))]]])

;; # Entity Normalization
;;
;; Once entities are extracted, they must be transformed into **canonical forms** for
;; consistent analysis and aggregation.
;;
;; ## Why Normalization Matters
;;
;; Entity extraction is just the first step. Real-world text contains variations:
;;
;; 1. **Transliterations** - Different languages have different spellings (e.g., "Beijing" vs "Peking")
;; 2. **Grammatical forms** - Plurals, possessives, case variations ("comet" vs "comets" vs "Comet's")
;; 3. **Abbreviations** - "NASA" vs "National Aeronautics and Space Administration"
;; 4. **Referential variations** - "Zygimantas Medelis" vs "Mr. Medelis" vs "Medelis"
;;
;; ## The Complexity Challenge
;;
;; Even in a narrow domain like astronomy, normalization rules become complex quickly.
;; The prompt below handles multiple cases, but in production you might need separate
;; prompts for different entity types.

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


(defn only-confirmed-entities [entities validations]
  (reduce (fn [m [e v]] (if v (conj m e) m))
          []
          (map vector entities validations)))

;; ## Normalization in Action
;;
;; Let's normalize the entities we extracted and verified from the GPT-NER pipeline.

^{:nextjournal.clerk/visibility {:result :hide}}
(def confirmed-entities
  (only-confirmed-entities
   (-> gpt-ner-extraction g/completions :extractor)
   (-> gpt-ner-verification g/completions :verifier)))

^{:nextjournal.clerk/visibility {:result :hide}}
(def normalized-entities
  (generate
   entity-standartization-prompt
   {:entity-type "celestial body"
    :entities    confirmed-entities
    :text        gpt-ner-test-text}))

;; ### Before and After Normalization

^{:nextjournal.clerk/visibility {:code :hide}}
(clerk/html
  [:div.block.p-6.bg-white.border.border-gray-200.rounded-lg.shadow.dark:bg-gray-800.dark:border-gray-700
   [:div.mb-4
    [:strong "Extracted Entities:"]
    [:div.font-mono.ml-4 (str confirmed-entities)]]
   [:div
    [:strong "Normalized Entities:"]
    [:div.font-mono.ml-4 (str (g/completions normalized-entities :standartizer))]]])

;; **The normalization process:**
;;
;; - Converts proper nouns to standard capitalization
;; - Reduces common nouns to singular form
;; - Preserves inherently plural entities
;; - Enables accurate frequency counting and entity resolution

;; # Bringing It All Together with Bosquet
;;
;; This notebook demonstrates how **Bosquet** simplifies complex NER pipelines:
;;
;; 1. **Flexible Prompting** - Mix template strings, Selmer syntax, and Clojure code seamlessly
;; 2. **Memory Integration** - Use simple-memory for development, scale to vector databases for production
;; 3. **Pipeline Composition** - Chain extraction, verification, and normalization steps naturally
;; 4. **Output Parsing** - Custom parsers transform LLM output into structured data
;;
;; ## Key Takeaways
;;
;; - **Traditional NER** works well for general domains but struggles with specialized vocabulary
;; - **LLM-based NER** offers flexibility and domain adaptation at the cost of compute
;; - **PromptNER** uses detailed reasoning and few-shot examples
;; - **GPT-NER** employs delimiter-based extraction with verification
;; - **Entity normalization** is essential for practical applications
;;
;; ## Next Steps
;;
;; For production NER systems, consider:
;;
;; - Caching normalized entities to reduce redundant processing
;; - Combining multiple extraction methods and voting on results
;; - Fine-tuning smaller models on domain-specific data
;; - Using entity linking to connect entities to knowledge bases
;;
;; Try adapting these techniques to your own domain - legal documents, medical records,
;; financial reports, or any specialized text corpus!
