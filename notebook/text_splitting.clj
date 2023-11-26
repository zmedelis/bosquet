;; ## Text chunking
;;
;; Text chunking is the process of breaking a text into parts. It is an essential part
;; of working with LLMs, since they can only process a limited amount of text at once.
;; Even as LLM context windows grow text chunking remains important. LLMs are U shaped
;; reasoners, they are good at remembering the beginning of a text and the end of a text,
;; but are not great at dealing with content in the middle.
;;
;; Thus text chunking can help with increasing relevancy of LLM based extraction and generation.

;; ### Chunking strategies

(ns text-splitting
  (:require
   [bosquet.llm.generator :as g]
   [bosquet.nlp.splitter :as split]
   [clojure.string :as string]
   [helpers :as h]
   [nextjournal.clerk :as clerk]))

;; Text can be split using different splitting units. *Bosquet* supports splitting by:
;; - characters
;; - tokens
;; - sentences
;;
;; Additional important splitting feature is the overlap between chunks. This helps to prevent
;; loosing context information at the chunk boundaries.

;; Example text (first paragraph from 'Moby Dick') to experiment with different chunking approaches

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
;;#### Splitting by characers
;;
;; Splitting by characters will take the text and chop it every N characters. A strategy
;; probebly best used for text that have some known structure or have some data regularity:
;; tables, CSV content, etc.
;;
;; Here the text will be split every `140` characters with `10` characters overlap.
;; Note how `10` characters from the `N-1` chunk are included at the beggingin of the `N` chunk.

(def char-chunks (split/chunk-text
                  {split/chunk-size 200
                   split/overlap    20
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
;; Splitting by sentences will take the text and chop it every N sentences. This results in
;; text segments that are natural for the human reader and will also prevent cutting the meaning
;; of the sentence into two chunks. For this reason sentence splitting the need for `overlap` is less
;; important. However, long sentences might result in overflows of the context window of the LLM.
;; (An imprvement would be needed to sentence splitter to prevent this.)
;;
;; *Note:* that `overlap` is not specified in the example below, thus the default value of `0` is used.
;;

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

;; ## Using text chunking with LLM
;;
;; A typical example of using text chunking would be to send parts parts of the longer text to LLM for
;; processing separately and then aggregating the results.
;;
;; Lets extract the feelings expressed by the character in the Moby Dick in that first paragraph of the book.
;;

(def extraction-prompt
  "You are a brillian reader of human emotions. Your ability to analyze text is unparalleled.
Please analyze the text bellow, and provide a list emotions expressed by the character in that text.

Reply with one or two words name for the empotion. Please refrain from further explanations.
If no emotions are epressed, reply with 'no emotions expressed'.

TEXT: {{chunk}}")

(def char-analysis
  (mapv
   #(g/generate extraction-prompt {:chunk %})
   char-chunks))

(def sentence-analysis
  (mapv
   #(g/generate extraction-prompt {:chunk %})
   sentence-chunks))

(def token-analysis
  (mapv
   #(g/generate extraction-prompt {:chunk %})
   token-chunks))


;; ### Characters

^{:nextjournal.clerk/visibility {:code :hide}}
(h/card-list (map :gen char-analysis))

;; ### Sentences

^{:nextjournal.clerk/visibility {:code :hide}}
(h/card-list (map :gen sentence-analysis))

;; ### Tokens

^{:nextjournal.clerk/visibility {:code :hide}}
(h/card-list (map :gen token-analysis))

;; ### Summary

(def summarization-proompt
  "You are provided with a list of expressions of emotions. Please aggregate them into
a single list of unique expressions. Omit any duplicates and skip 'no empotions expressed' entries.
Respond with unnumbered bullet list and nothing else.

EMOTIONS: {{emotions}}")

(g/generate summarization-proompt {:emotions (string/join ", " (mapv :gen char-analysis))})

(g/generate summarization-proompt {:emotions (string/join ", " (mapv :gen sentence-analysis))})

(g/generate summarization-proompt {:emotions (string/join ", " (mapv :gen token-analysis))})
