(ns bosquet.nlp.splitter
  (:require
   [bosquet.llm.openai-tokens :as oai]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [flatland.useful.seq :as useq]
   [taoensso.timbre :as timbre])
  (:import
   [opennlp.tools.sentdetect SentenceDetectorME SentenceModel]))

(def chunk-size
  "Since of the chunks in which the text gets split."
  :splitter/chunk-size)

(def split-unit
  "Lexical units in which the text gets split: character, token, sentence."
  :splitter/unit)

(def overlap
  "Number of units by which chunks can overlap."
  :splitter/overlap)

(def model
  "Model used for tokenization."
  :splitter/model)

(def sentence
  "Text splitter by sentences. It will use OpenNLP sentnce splitter to partition
  the text."
  :splitter/sentence-split)

(def character
  "Text splitter by individual characters."
  :splitter/character-split)

(def token
  "Text splitter by tokens. Tokenization is done based on the provided model."
  :splitter/token-split)

(defn ^{:deprecated true} split-max-tokens
  "DEPRECATED use `text-chunker` instead with `split-unit` set to `token`.

  Splits a given string `text` in several sub-strings.
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

(defn- load-splitter-model
  "Load OpenNLP model for a `lang` sentence boundary detection.

  See https://opennlp.apache.org/models.html"
  [lang]
  (let [model-file (io/file (format "models/lang/%s/sentence-detector.bin" (name lang)))]
    (if (.exists model-file)
      (SentenceDetectorME. (SentenceModel. model-file))
      (timbre/errorf
        "‼️ Sentence detenction model file is not found. Use `bb lang:sent:%s` to download."
        (name lang)))))

(defn- text-units-length
  [units]
  (reduce (fn [cnt chunk] (+ cnt (count chunk))) 0 units))

(defn text-splitter
  [{:splitter/keys [chunk-size overlap]
    :or            {overlap 0}}
   text-units]
  (let [unit-count (count text-units)]
    (loop [chunks      []
           current-pos (text-units-length chunks)]
      (if (> current-pos unit-count)
        chunks
        (recur
         (conj chunks
               (subvec text-units
                       (max (- current-pos overlap) 0)
                       (min (+ (- current-pos
                                  ;; cant figure nicer way, this if is needed
                                  ;; to avoid getting the first chunk made shorter by overlap
                                  (if (zero? current-pos) 0 overlap)) chunk-size)
                            unit-count)))
         (+ current-pos (- chunk-size
                           (if (zero? current-pos) 0 overlap))))))))

(defn- text->sentences
  "Split `text` into sentences using OpenNLP sentence splitting model"
  [{:keys [lang] :or {lang :en}} text]
  (-> lang
      load-splitter-model
      (.sentDetect text)
      vec))

(defn- text<-sentences
  [_opts sentences]
  (string/join " " sentences))

(defn- text->characters
  [_opts text]
  (vec (map identity text)))

(defn- text<-characters
  [_opts chars]
  (string/join chars))

(defn- text->tokens [{:splitter/keys [model]} text]
  (vec (oai/encode text model)))

(defn- text<-tokens [{:splitter/keys [model]} text]
  (oai/decode text model))

(def split-handlers
  "Split handlers are needed to turn text into specified text units via `encode` function.
  `decode` function will turn those units back into single text string."
  {sentence  {:encode text->sentences
              :decode text<-sentences}
   character {:encode text->characters
              :decode text<-characters}
   token     {:encode text->tokens
              :decode text<-tokens}})

(defn chunk-text
  "Chunk `text` into `chunk-size` blocks using specified `splitter`. Optionaly
  `overlap` can be specified by how many text units chunks can overap (defaults to 0).

  Supported text splitters:
  - `sentence-splitter`
  - `character-splitter`
  - `token-splitter`"
  [{:splitter/keys [unit] :as opts} text]
  (let [{:keys [encode decode]} (split-handlers unit)
        encode                  (partial encode opts)
        decode                  (partial decode opts)]
    (->> text
         encode
         (text-splitter opts)
         (map decode))))

(comment

  (def text (slurp "https://raw.githubusercontent.com/scicloj/scicloj.ml.smile/main/LICENSE"))

  (text->sentences nil text)

  (tap>
   (chunk-text {chunk-size 3 split-unit sentence} text))

  (chunk-text {chunk-size 10 split-unit token model :gpt-4}
              "Think not, is my eleventh commandment; and sleep when you can, is my twelfth.")
  #__)
