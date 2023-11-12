(ns bosquet.llm.openai-tokens
  "JTokkit wrapper to get encode/decode and get token counts.
  Plus a price estimator for model produced tokens"
  (:import
   [com.knuddels.jtokkit Encodings]))

;;
;; ## Encodings and token counting
;;

(defonce registry (Encodings/newDefaultEncodingRegistry))

(defn encoding
  "Get encoding by model name. Name is provided as keyword matching names specified in
   - https://platform.openai.com/docs/models/overview

  If model name is not found it will throw `NoSuchElementException` exception. "
  [model]
  (.get (.getEncodingForModel registry (name model))))

(defn encode
  "Encode `text` using `model` (:gpt-4, :gpt-3.5-turbo)"
  [text model]
  (.encode (encoding model) text))

(defn decode
  "Encode `tokens` using `model`"
  [tokens model]
  (.decode (encoding model) tokens))

(defn token-count
  "Count tokens in the `text` using `model` for token production. "
  [text model]
  (count (encode text model)))

(comment
  (def text (:text (bosquet.read.document/parse "data/llama2.pdf")))
  (token-count text :gpt-4)
  #__)

;;
;; ## Pricing
;;

(def pricing
  "OAI model prices (per 1k tokens) and token limits specified in a map:
  - `input` price for the prompt tokens
  - `output` price for the completion tokens
  - `tokens` max context tokens the model supports"

  {:gpt-4             {:input  0.003
                       :output 0.006
                       :tokens 8192}
   :gpt-3.5-turbo-16k {:input  0.003
                       :output 0.004
                       :tokens 16384}
   :gpt-3.5-turbo     {:input  0.0015
                       :output 0.002
                       :tokens 4096}
   :text-ada-001      {:input  0.0004
                       :output 0.0004
                       :tokens 2049}
   :text-babbage-001  {:input  0.0005
                       :output 0.0005
                       :tokens 2049}
   :text-curie-001    {:input  0.002
                       :output 0.002
                       :tokens 2049}
   :text-davinci-003  {:input  0.02
                       :output 0.02
                       :tokens 4097}})

(defn price-estimate
  "Estimate price for the `prompt` and `completion` using `model`.
  If `prompt` and `completion` are strings it will count tokens first.
  If `prompt` and `completion` are numbers it will assume they are token counts"
  ([prompt model] (price-estimate prompt "" model))
  ([prompt completion model]
   (let [{:keys [input output]} (model pricing)]
     ;; If we have got strings count tokens first
     (cond
       (and (string? prompt) (string? completion))
       (+
        (* (token-count prompt model) input)
        (* (token-count completion model) output))
       ;; If we have got numbers it must be token counts already
       (and (number? prompt) (number? completion))
       (+
        (* prompt input)
        (* completion output))))))

(defn fits-in-context-window? [token-count model]
  (>= (get-in pricing [model :tokens]) token-count))
