(ns bosquet.nlp.splitter
  (:require
   [bosquet.llm.openai-tokens :as oai]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [flatland.useful.seq :as useq])
  (:import
   [opennlp.tools.sentdetect SentenceDetectorME SentenceModel]))

(defn split-max-tokens
  "Splits a given string `text` in several sub-strings.
  Each split will have maximum length, while having less tokens then `max-token`.
  The numer of tokens of a substring gets obtained by calling `token-count-fn` with the current string repeatedly
  while growing the string.
  Initialy the text is split by `split-char`, which should nearly always be a form of whitespace.
  Then the substring are grown by the result of the splitting.
  Keeping `split-char` as whitespace avoids that words get split in the middle.

  In very rare situations where the text has words longer then `max-token`, the function
  might return substrings which have more tokens then `max-token`. In this case `split-char` could be modified
  to split on something else then word boundaries, which will then eventualy break words in the middle,
  but would guaranty that substrings do not have more then `max-token` tokens."
  ([text max-tokens model split-char]
   (let [batches
         (useq/glue
          (fn [s c]
            (concat s [c]))
          []
          (fn [current-batch item]
            (let [s (string/join split-char (concat current-batch [item]))
                  c (oai/token-count s model)]
              (< c max-tokens)))

          (string/split text (re-pattern split-char)))]
     (map
      #(string/join split-char %)
      batches)))
  ([text max-tokens model]
   (split-max-tokens text max-tokens model " ")))

(def en-sentence-detector
  "English sentence splitting model

  https://opennlp.apache.org/models.html"
  (SentenceDetectorME.
   (-> "models/opennlp-en-ud-ewt-sentence-1.0-1.9.3.bin"
       io/resource
       SentenceModel.)))

(defn- text-units-length
  [units]
  (reduce (fn [cnt chunk] (+ cnt (count chunk))) 0 units))

(defn text-splitter
  [{:keys [chunk-size overlap]
    :or   {overlap 0}} text-units]
  (let [unit-count  (count text-units)]
    (loop [chunks      []
           current-pos (text-units-length chunks)]
      (if (> current-pos unit-count)
        chunks
        (recur
         (conj chunks
               (subvec text-units
                       (max (- current-pos overlap) 0)
                       (min (+ (- current-pos overlap) chunk-size) unit-count)))
         (+ current-pos chunk-size))))))

(defn text->sentences
  "Split `text` into sentences using OpenNLP sentence splitting model"
  [text]
  (vec (.sentDetect en-sentence-detector text)))

(defn text<-sentences
  [sentences]
  (string/join " " sentences))

(defn text->characters
  [text]
  (vec (map identity text)))

(defn text<-characters
  [chars]
  (string/join chars))

(def sentence-splitter
  "Text splitter by sentences. It will use OpenNLP sentnce splitter to partition
  the text into a vector of sentences"
  ::sentence-splitter)

(def character-splitter
  "Text splitter by individual characters. Text will be turned into array of characers."
  ::character-splitter)

(def ^:private split-handlers
  "Split handlers are needed to turn text into specified text units via `encode` function.
  `decode` function will turn those units back into single text string."
  {sentence-splitter  {:encode text->sentences
                       :decode text<-sentences}
   character-splitter {:encode text->characters
                       :decode text<-characters}})

(defn text-chunker
  "Chunk `text` into `chunk-size` blocks using specified `splitter`. Optionaly
  `overlap` can be specified by how many text units chunks can overap (defaults to 0).

  TODO `overlap` is currently failing, see unit-test

  Supported text splitters:
  - `sentence-splitter`
  - `character-splitter`
  - TODO `token-splitter`"
  [{:keys [splitter] :as opts} text]
  (let [{:keys [encode decode]} (split-handlers splitter)]
    (->> text
         encode
         (text-splitter opts)
         (map decode))))

(comment

  (def text (slurp "https://raw.githubusercontent.com/scicloj/scicloj.ml.smile/main/LICENSE"))

  (tap>
   (text-chunker {:chunk-size 3 :splitter sentence-splitter} text))

  (tap>
   (text-chunker
    {:chunk-size 10 :splitter character-splitter}
    "Never attempt to win by force what can be won by deception"))

  (->> (split-max-tokens text 12 "gpt-4")
       (map #(hash-map :c (oai/token-count % "gpt-4") :s %))
       (map :c))
  (split-max-tokens text 10 "gpt-4")
  #__)
