(ns bosquet.generator-test
  (:require
   [bosquet.llm.chat :as llm.chat]
   [bosquet.llm.generator :refer [all-keys chat generate]]
   [bosquet.llm.openai :as openai]
   [clojure.test :refer [deftest is]]
   [matcher-combinators.matchers :as m]
   [matcher-combinators.test :refer [match?]]))

(defn dummy-generator [_text {model :model}]
  (condp = model
    "galileo" "0.0017 AU"
    "hubble"  "Yes"
    (throw (ex-info (str "Unknown model: " model) {}))))

(defn dummy-chat [messages _opts]
  {:role :dummy-assistant
   :content (str "I have " (count messages))})

(def astronomy-prompt
  {:role            "As a brilliant {{you-are}} answer the following question."
   :question-answer "Question: {{question}} Answer: {% gen var-name=answer model=galileo %}"
   :self-eval       "{{answer}}. Is this a correct answer? {% gen var-name=test model=hubble %}"})

(deftest keys-to-produce
  (is (match? (m/in-any-order [:role :question :question-answer :self-eval :you-are :answer :test])
              (all-keys astronomy-prompt {:you-are "astronomer" :question "How far to X?"}))))

(deftest generation-with-different-models
  (is
   (match?
    {:role            "As a brilliant astronomer answer the following question."
     :question        "What is the distance from Moon to Io?"
     :question-answer "Question: What is the distance from Moon to Io? Answer: 0.0017 AU"
     :self-eval       "0.0017 AU. Is this a correct answer? Yes"
     :test            "Yes"
     :you-are         "astronomer"}
    (with-redefs [openai/complete dummy-generator]
      (generate astronomy-prompt
                {:you-are  "astronomer"
                 :question "What is the distance from Moon to Io?"}
                {:self-eval       {:bosquet.llm/service          [:llm/openai :provider/openai]
                                   :bosquet.llm/model-parameters {:model "hubble"}}
                 :question-answer {:bosquet.llm/service          [:llm/openai :provider/openai]
                                   :bosquet.llm/model-parameters {:model "galileo"}}})))))

(deftest fail-generation
  (is (match?
       {:role            "As a brilliant astronomer answer the following question."
        :question        "What is the distance from Moon to Io?"
        :question-answer "Question: What is the distance from Moon to Io? Answer: 0.0017 AU"
        :self-eval       nil
        :test            nil
        :you-are         "astronomer"}
       (with-redefs [openai/complete dummy-generator]
         (generate
          astronomy-prompt
          {:you-are  "astronomer"
           :question "What is the distance from Moon to Io?"}
          {:question-answer {:bosquet.llm/service          [:llm/openai :provider/openai]
                             :bosquet.llm/model-parameters {:model "galileo"}}
           :self-eval       {:bosquet.llm/service          [:llm/openai :provider/openai]
                             :bosquet.llm/model-parameters {:model "AGI"}}})))))

(deftest chat-message-construction
  (is (= {llm.chat/conversation [{:content "I have 2" :role :dummy-assistant}]
          llm.chat/last-message {:content "I have 2" :role :dummy-assistant}
          llm.chat/system "You are a helpful assistant."}
         (with-redefs [openai/chat-completion dummy-chat]
           (chat
            {llm.chat/system "You are a helpful assistant."}
            {}
            :user "Why the sky is blue?"
            {llm.chat/conversation {:bosquet.llm/service [:llm/openai :provider/openai]
                                    :bosquet.llm/model-parameters {:temperature 0
                                                                   :model "hubble"}}})))))
