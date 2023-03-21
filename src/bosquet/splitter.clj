(ns bosquet.splitter
  (:require [flatland.useful.seq :as useq]
            [clojure.string :as str]))


(defn heuristic-gpt-token-count-fn [^String s]
  (int (/  (.length s) 4)))

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
  but would guaranty that substrings do not have more then `max-token` tokens.
  "
  ([text max-tokens token-count-fn split-char]
   (let [batches
         (useq/glue
          (fn [s c]
            (concat s [c]))
          []
          (fn [current-batch item]
            (let [s (str/join split-char (concat current-batch [item]))
                  c (token-count-fn s)]
              (< c max-tokens)))

          (str/split text (re-pattern split-char)))]
     (map
      #(str/join split-char %)
      batches)))
  ([text max-tokens token-count-fn]
   (split-max-tokens text max-tokens token-count-fn " ")))


(comment
  ;; requires tiktoken/jar {:local/root "tiktoken-1.0-SNAPSHOT.jar"} in deps.edn
  ;; from https://github.com/eisber/tiktoken
  ;;
  ;; (import '[tiktoken Encoding])

  (defn gpt-count [encoding s]
    (count (.encode encoding
                    s
                    (into-array String []) Integer/MAX_VALUE)))

  ;; (def enc (Encoding. "text-davinci-001"))

  (def text (slurp "https://raw.githubusercontent.com/scicloj/scicloj.ml.smile/main/LICENSE"))


  (->>
   (split-max-tokens
    text
    12
    (fn [s] (gpt-count enc s)))
   (map #(hash-map :c  (gpt-count enc %)
                   :s %))
   (map :c))


  :ok)
