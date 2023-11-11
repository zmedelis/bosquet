(ns bosquet.nlp.splitter
  (:require
   [bosquet.llm.openai-tokens :as oai]
   [clojure.string :as str]
   [flatland.useful.seq :as useq]))

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
            (let [s (str/join split-char (concat current-batch [item]))
                  c (oai/token-count s model)]
              (< c max-tokens)))

          (str/split text (re-pattern split-char)))]
     (map
      #(str/join split-char %)
      batches)))
  ([text max-tokens model]
   (split-max-tokens text max-tokens model " ")))

(defn character-splitter
  "Character based chunker. It will split tekst and `chunk-size` bits.
  If `overlap` is specified those chunks will overlap by specified number
  of characters"
  [text {:keys [chunk-size overlap]
         :or   {chunk-size 12000 overlap 0}}]
  (loop [current-pos 0
         chunks      []]
    (if (> current-pos (count text))
      chunks
      (recur
       (+ current-pos chunk-size)
       (conj chunks
             (subs text
                   (max (- current-pos overlap) 0)
                   (min (+ (- current-pos overlap) chunk-size) (count text))))))))

(comment
  (defn gpt-count [encoding s]
    (count (.encode encoding s)))

  (def text (slurp "https://raw.githubusercontent.com/scicloj/scicloj.ml.smile/main/LICENSE"))

  (->> (split-max-tokens text 12 "gpt-4")
       (map #(hash-map :c (oai/token-count % "gpt-4") :s %))
       (map :c))
  (split-max-tokens text 10 "gpt-4")
  :ok)
