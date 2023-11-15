(ns bosquet.eval.qna-generator
  (:require
   [bosquet.llm.generator :as gen]
   [bosquet.llm.openai-tokens :as otok]
   [bosquet.nlp.splitter :as splitter]
   [bosquet.read.document :as document]
   [bosquet.utils :as u]
   [bosquet.wkk :as wkk]
   [malli.core :as m]
   [clojure.java.io :as io]
   [taoensso.timbre :as timbre]
   [clojure.core :as c]))

;; Some of the details are borrowed from
;; https://github.com/run-llama/llama_index/blob/29ef306ae0536de44840ca5acfdf93d84b9a560c/llama_index/evaluation/dataset_generation.py

(def query-count :eval/query-count)

(def context-prompt-block
  (u/join-nl
   "CONTEXT INFORMATION is below:"
   "~~~~~~"
   "{{context}}"
   "~~~~~~"))

(def format-constraints
  #_"Write your response as numbered list, one item per line."
  (u/join-nl
   "Write your response in JSON. Resulting JSON is a vector containing generated items one vector element per generated item."
   "Example JSON output: [\"Item 1\", \"Item 2\", \"Item 3\", \"Item 4\"]"))

(def question-building-prompts
  {:role
   (u/join-nl
    "You are an excelent Teacher who understands subject material perfectly."
    "Your ability to analyze text is astonishing. Based on that your task is to setup"
    "{{question-count}} questions for the upcoming student examination."
    "The questions should be diverse and cover interesting and important topics and facts across the document."
    "Restrict the {{question-count}} questions you are writing to the CONTEXT INFORMATION provided.")

   :format
   "Write your response as numbered list, one question per line."

   :question-generation context-prompt-block

   :qna
   (u/join-nl
    "{{role}}"
    "{{question-generation}}"
    "{{format}}"
    ""
    "QUESTIONS:"
    "{% gen var-name=questions%}")})

(def answering-prompt
  (u/join-nl
   context-prompt-block
   "Given the CONTEXT INFORMATION and using zero prior knowledge, answer the following QUESTIONS."
   "QUESTIONS"
   "{% for q in queries %}"
   #_"{{forloop.counter}}. {{q}}{% endfor %}"
   "* {{q}}{% endfor %}"
   ""
   "Answer questions in exact same order as they are listed in QUESTIONS. Answer exactly the same list of questions as provided."
   format-constraints
   "ANSWERS:"
   "{% gen %}"))

(def question-set
  [:sequential [:string {:min 2 :max 400}]])

(def response-set
  [:sequential [:string {:min 6 :max 1200}]])

(defn- query-response-valid?
  "Check the validity of questions and responses.

  A naive check see if:
  * the lenght of question collection is the same as answers
  * collections are min lenght strings as per schema"
  [{:keys [queries responses context]}]
  (if (and
       (m/validate question-set queries)
       (m/validate response-set responses)
       (= (count responses) (count responses)))
    true
    (do
      (timbre/warnf "Query (count:%s) / Response (count: %s) is invalid. Context: '%s'"
                    (count queries) (count responses)
                    (u/safe-subs context 0 200))
      false)))

(defn generate-qna-dataset
  [{q-count query-count} document]
  (let [chunks       (splitter/text-chunker
                      {:chunk-size (* q-count 45) :splitter splitter/sentence-splitter}
                      document)
        model        #_"gpt-3.5-turbo" #_"gpt-4-1106-preview" "gpt-3.5-turbo-1106"
        q-gen-params {:questions
                      {wkk/service          wkk/oai-service
                       wkk/output-format    :list
                       wkk/model-parameters {:temperature 0.0 :max-tokens (* q-count 100) :model model}}}
        a-gen-params {:gen
                      {wkk/service          wkk/oai-service
                       wkk/output-format    :json
                       wkk/model-parameters {:temperature 0.0 :max-tokens (* q-count 400) :model model}}}

        xf (comp
            (map
             (fn [chunk]
               (timbre/debugf "QnA for chunk with token count -  %s" (otok/token-count chunk model))
               (let [{questions :questions :as g}
                     (gen/generate
                      question-building-prompts
                      {:question-count q-count :context chunk}
                      q-gen-params)

                     {resp :gen :as answers}
                     (gen/generate
                      answering-prompt
                      {:queries questions :context chunk}
                      a-gen-params)]
                 (tap> {'answers answers
                        'g g})
                 {:queries questions
                  :responses resp
                  :context chunk})))
             ;; TODO instead of filtering out - retry
            (filter query-response-valid?))]
    (into [] xf
          chunks
          #_(take 8 chunks))))

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
  [opts document-file dataset-file]
  (->> document-file
       document/parse
       :text
       (generate-qna-dataset opts)
       (qna->eval-dataset document-file)
       (save-dataset dataset-file)))

(comment
  (document->dataset {query-count 4} "data/llama2.pdf" "data/llama2-eval.edn")

  (def text (:text (document/parse "data/llama2.pdf")))
  (def qna (generate-qna-dataset {query-count 3} text))
  (tap> qna)

  (gen/generate
   question-building-prompts
   {:question-count 3
    :context
    (second (splitter/text-chunker
             {:chunk-size 25 :splitter splitter/sentence-splitter}
             text))}
   {:questions
    {wkk/service          wkk/oai-service
     wkk/output-format    :list
     wkk/model-parameters {:temperature 0.0 :max-tokens 500 :model "gpt-3.5-turbo-1106"}}})
  #__)
