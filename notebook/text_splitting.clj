^{:nextjournal.clerk/visibility {:code :fold}}
(ns text-splitting
  {:nextjournal.clerk/toc true}
  (:require
   [bosquet.llm.generator :as g]
   [bosquet.nlp.splitter :as split]
   [clojure.string :as string]
   [helpers :as h]
   [nextjournal.clerk :as clerk]))

;; # Text chunking
;;
;; Text chunking is the process of breaking a text into parts. It is an essential part of
;; working with LLMs since they can only process a limited amount of text. Even as LLM context
;; windows grow, text chunking remains important. LLMs are U-shaped reasoners. They are good
;; at remembering the beginning and the end of a text but are not great at dealing with content
;; in the middle.

;; Thus text chunking can help to increase the relevancy of LLM-based extraction and text
;; generation.

;; ### Chunking strategies

;; Text splitting can be done by using different splitting units. *Bosquet* supports splitting by:
;;
;; - characters
;; - tokens
;; - sentences
;;
;; An additional important splitting feature is the overlap between chunks. This helps to
;; prevent losing context information at the chunk boundaries. Example text (first paragraph
;; from *Moby Dick*) to experiment with different chunking approaches

^{:nextjournal.clerk/visibility {:result :hide}}
(def text
  "Call me Ishmael. Some years ago—never mind how long precisely—having
little or no money in my purse, and nothing particular to interest me
on shore, I thought I would sail about a little and see the watery part
of the world. It is a way I have of driving off the spleen and
regulating the circulation. Whenever I find myself growing grim about
the mouth; whenever it is a damp, drizzly November in my soul; whenever
I find myself involuntarily pausing before coffin warehouses, and
bringing up the rear of every funeral I meet; and especially whenever
my hypos get such an upper hand of me, that it requires a strong moral
principle to prevent me from deliberately stepping into the street, and
methodically knocking people’s hats off—then, I account it high time to
get to sea as soon as I can.")

;;
;; #### Splitting by characters
;;
;; Splitting by characters will take the text and chop it at every N-th character. This
;; strategy is probably best used for text that has a known structure or a regular data form:
;; tables, CSV content, etc.
;;
;; Here, the text will be split at every 140 characters with the 10 characters overlap.
;; *Note*, how 10 characters from the N-1 chunk are included at the beginning of the N chunk.

(def char-chunks (split/chunk-text
                  {split/chunk-size 200
                   split/overlap    10
                   split/split-unit split/character}
                  text))


^{:nextjournal.clerk/visibility {:code :hide}}
(clerk/html
  (vec
    (cons
      :div.font-mono
      (map
        (fn [chunk]
          [:div.block.p-6.bg-white.border.border-gray-200.rounded-lg.shadow.hover:bg-gray-100.dark:bg-gray-800.dark:border-gray-700.dark:hover:bg-gray-700.grid.grid-cols-1.gap-3
           [:div.flex
            [:div chunk]]])
        char-chunks))))

;;
;; #### Splitting by sentences
;;
;; Sentence splitting relies on OpenNLP models - https://opennlp.apache.org/models.html
;;
;; **NOTE:** They need to be downloaded before using this functionality.
;;
;; Running `bb lang:sent:en` will download the English sentence splitting model and place it in `lang/en` directory.
;;
;; Splitting by sentences will partition the text into chunks of N sentences. This results in
;; chunks that are natural to reader. It will also prevent cutting the meaning of the sentence
;; into two chunks. For this reason, the need for overlap parameter is less important when
;; using this splitting method. However, long sentences might result in overflows of the context
;; window of the LLM.
;;
;; **Note:** that overlap is not specified in the example below - the default of 0 is used.

(def sentence-chunks (split/chunk-text
                      {split/chunk-size 3
                       split/split-unit split/sentence}
                      text))

^{:nextjournal.clerk/visibility {:code :hide}}
(clerk/html
  (vec
    (cons
      :div.font-mono
      (map
        (fn [chunk]
          [:div.block.p-6.bg-white.border.border-gray-200.rounded-lg.shadow.hover:bg-gray-100.dark:bg-gray-800.dark:border-gray-700.dark:hover:bg-gray-700.grid.grid-cols-1.gap-3
           [:div.flex
            [:div chunk]]])
        sentence-chunks))))

;;
;; #### Splitting by tokens
;;
;; Splitting by tokens will take the text and chop it every N tokens. This is the most convenient and
;; safe way to split text for LLMs. With token splitting there is full control of exactly how many
;; tokens are used in a given split, thus we can be sure to prevent overflows of the context window.

(def token-chunks (split/chunk-text
                   {split/chunk-size 50
                    split/overlap    5
                    split/split-unit split/token
                    split/model      :gpt-4}
                   text))

^{:nextjournal.clerk/visibility {:code :hide}}
(clerk/html
  (vec
    (cons
      :div.font-mono
      (map
        (fn [chunk]
          [:div.block.p-6.bg-white.border.border-gray-200.rounded-lg.shadow.hover:bg-gray-100.dark:bg-gray-800.dark:border-gray-700.dark:hover:bg-gray-700.grid.grid-cols-1.gap-3
           [:div.flex
            [:div chunk]]])
        token-chunks))))

;; ### Generation with chunking
;;
;; An example of using text chunking would be to send parts of the longer text to LLM for
;; processing separately and then aggregating the results. Let's extract the feelings expressed
;; by the character in Moby Dick's first paragraph (pretending that it is a very long text).
;;

(def extraction-prompt
  "You are a brillian reader of human emotions. Your ability to analyze text is unparalleled.
Please analyze the text bellow, and provide a list emotions expressed by the character in that text.

Reply with one or two words name for the empotion. Please refrain from further explanations.
If no emotions are epressed, reply with 'no emotions expressed'. Provide your response as
a bullet list.

TEXT: {{chunk}}")

(defn analysis
  [chunks]
  (mapv
   #(g/generate extraction-prompt {:chunk %})
   chunks))

;; Bellow per chunker resutls show how chunking methid can influence the output. The three methods
;; return quite different extracted emotions.
;;
;; #### Character splitter

(def char-results (analysis char-chunks))

^{:nextjournal.clerk/visibility {:code :hide}}
(h/card-list (mapv clerk/md char-results))

;; #### Sentence splitter

(def sentence-results (analysis sentence-chunks))

^{:nextjournal.clerk/visibility {:code :hide}}
(h/card-list (mapv clerk/md sentence-results))

;; #### Token splitter

(def token-results (analysis token-chunks))

^{:nextjournal.clerk/visibility {:code :hide}}
(h/card-list (mapv clerk/md token-results))


;; #### Summary
;;
;; All the chunk results need to be consolidated into a single list of unique emotions. This can
;; be done with another LLM request that gets all the per chunk detected emotions and aggregates
;; them into a single list.

(def summarization-prompt
  {:prompt
   "You are provided with a list of expressions of emotions. Please aggregate them into
a single list of summarizing emotions. Omit any duplicates and skip 'no empotions expressed' entries.
Respond with unnumbered bullet list and nothing else.

EMOTIONS: {{emotions}}

{{summary}}"
   :summary (g/llm :mistral-medium)})

(defn summarize [analysis]
  (->
   summarization-prompt
   (g/generate {:emotions (string/join ", " analysis)})
   g/completions
   :summary))

(clerk/table [["Character split" (clerk/md (summarize char-results))]
              ["Sentence split" (clerk/md (summarize sentence-results))]
              ["Token split" (clerk/md (summarize token-results))]])
