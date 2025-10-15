(ns bosquet.eval.qna-generator
  (:require
   [bosquet.llm.generator :as gen]
   [bosquet.llm.openai-tokens :as otok]
   [bosquet.nlp.splitter :as splitter]
   [bosquet.read.document :as document]
   [bosquet.utils :as u]
   [bosquet.wkk :as wkk]
   [clojure.core :as c]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [taoensso.timbre :as timbre]))

;; Some of the details are borrowed from
;; https://github.com/run-llama/llama_index/blob/29ef306ae0536de44840ca5acfdf93d84b9a560c/llama_index/evaluation/dataset_generation.py

(def query-count :eval/query-count)
(def max-chunks :eval/max-chunks)

(def context-prompt-block
  (u/join-lines
   "CONTEXT INFORMATION is below:"
   "~~~~~~"
   "{{context}}"
   "~~~~~~"))

(def format-constraints
  #_"Write your response as numbered list, one item per line."
  (u/join-lines
   "Write your response in JSON. Resulting JSON is a vector containing generated items one vector element per generated item."
   "Example JSON output: [\"Item 1\", \"Item 2\", \"Item 3\", \"Item 4\"]"))

(defn question-building-prompts
  [question-count]
  {:role                ["You are an excelent Teacher who understands subject material perfectly."
                         "Your ability to analyze text is astonishing. Based on that your task is to setup"
                         "{{question-count}} questions for the upcoming student examination."
                         "The questions should be diverse and cover interesting and important topics and facts across the document."
                         "Restrict the {{question-count}} questions you are writing to the CONTEXT INFORMATION provided."]
   :format              "Write your response as numbered list, one question per line."
   :question-generation context-prompt-block
   :qna                 ["{{role}}"
                         "{{question-generation}}"
                         "{{format}}"
                         ""
                         "QUESTIONS:"
                         "{{questions}}"]
   :questions (gen/llm :gpt-4
                       wkk/output-format    :list
                       wkk/model-parameters {:max-tokens (* question-count 100)})})

(defn answering-prompt
  [question-count]
  {:questions [context-prompt-block
               "Given the CONTEXT INFORMATION and using zero prior knowledge, answer the following QUESTIONS."
               "QUESTIONS"
               "{% for q in queries %}"
               "* {{q}}{% endfor %}"
               ""
               "Answer questions in exact same order as they are listed in QUESTIONS. Answer exactly the same list of questions as provided."
               format-constraints
               "ANSWERS:"]
   :answers   (gen/llm :gpt-4
                       wkk/output-format    :json
                       wkk/model-parameters {:max-tokens (* question-count 400)})})

(defn- query-response-valid?
  "Check the validity of questions and responses.

  A naive check see if:
  * the lenght of question collection is the same as answers
  * collections are min lenght strings as per schema"
  [{:keys [queries responses context]}]
  (if (= (count responses) (count queries))
    true
    (do
      (timbre/warnf
       "Query (count:%s) / Response (count: %s) is invalid. Context: '%s'"
       (count queries) (count responses) (u/safe-subs context 0 200))
      false)))

(defn generate-qna-dataset
  "Generate a QnA dataset. First a `document` will be loaded and split into sentence chunks.
  The size of the chunk is a function of how many questions we are generating per chunk.

  Passed in options control the scope of the QnA generation process:
  - `query-count`: how many queries per chunk to generate
  - `max-chunks`: how many chunks to take, nil will take them all"
  [{q-count query-count chunk-count max-chunks} document]
  (let [n-sentence   35
        chunks       (splitter/chunk-text
                      {splitter/chunk-size (* q-count n-sentence)
                       splitter/split-unit splitter/sentence}
                      document)
        model        :gpt-4
        xf (comp
            (map
             (fn [chunk]
               (timbre/debugf "QnA for chunk with token count -  %s" (otok/token-count chunk model))
               (let [{:keys [questions]} (gen/generate
                                          (question-building-prompts q-count)
                                          {:question-count q-count :context chunk})
                     resp                (gen/generate
                                          (answering-prompt q-count)
                                          {:queries questions :context chunk})]
                 {:queries   questions
                  :responses resp
                  :context   chunk})))
             ;; TODO instead of filtering out - retry
            (filter query-response-valid?))]
    (into [] xf
          (if chunk-count
            (take chunk-count chunks)
            chunks))))

(defn qna->eval-dataset
  "Convert QnA data to a dataset format -  a list of question to answer tuples"
  [ds-id ds-data]
  {:dataset ds-id
   :eval    (vec (mapcat
                  (fn [{:keys [queries responses]}] (map vector queries responses))
                  ds-data))})

(defn save-dataset
  [ds-file ds]
  (io/make-parents ds-file)
  (spit ds-file (u/pp-str ds)))

(defn document->dataset
  "Given a `document-file` create a QnA dataset. Save it to
  `dataset-file`"
  [opts document-file dataset-file]
  (->> document-file
       document/parse
       :text
       (generate-qna-dataset opts)
       (qna->eval-dataset document-file)
       (save-dataset dataset-file)))

(defn load-qna-dataset
  "Load eval tuples from file (created via `save-dataset`)"
  [file-name]
  (-> file-name slurp edn/read-string :eval))

(comment
  (document->dataset {query-count 4} "data/llama2.pdf" "data/llama2-eval.edn")

  (load-qna-dataset "data/llama2-eval.edn")

  (def text (:text (document/parse "data/llama2.pdf")))
  (def qna (generate-qna-dataset {query-count 2 max-chunks 2} text))
  (tap> qna)

  (gen/generate
   (question-building-prompts 3)
   {:question-count 3
    :context (second (splitter/text-splitter
                      {:chunk-size 25 :splitter splitter/sentence-splitter}
                      text))})
  #__)
