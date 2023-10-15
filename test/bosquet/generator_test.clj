(ns bosquet.generator-test
  (:require
   [bosquet.complete :as complete]
   [bosquet.llm.chat :as llm.chat]
   [bosquet.llm.generator :refer [all-keys chat generate]]
   [bosquet.llm.llm :as llm]
   [bosquet.llm.openai :as openai]
   [bosquet.wkk :as wkk]
   [clojure.test :refer [deftest is]]
   [matcher-combinators.matchers :as m]
   [matcher-combinators.test :refer [match?]]))

(defn dummy-generator [_text {model :model} _opts]
  {llm/content
   {:completion
    (condp = model
      "galileo" "0.0017 AU"
      "hubble"  "Yes"
      (throw (ex-info (str "Unknown model: " model) {})))}})

(def astronomy-prompt
  {:role            "As a brilliant {{you-are}} answer the following question."
   :question-answer "Question: {{question}} Answer: {% gen var-name=answer model=galileo %}"
   :self-eval       "{{answer}}. Is this a correct answer? {% gen var-name=test model=hubble %}"})

(deftest keys-to-produce
  (is (match? (m/in-any-order [:role :question :question-answer :self-eval :you-are :answer :test])
              (all-keys astronomy-prompt {:you-are "astronomer" :question "How far to X?"}))))

(deftest generltion-with-different-models
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
                {:test   {wkk/service          [:llm/openai :provider/openai]
                          wkk/model-parameters {:model "hubble"}}
                 :answer {wkk/service          [:llm/openai :provider/openai]
                          wkk/model-parameters {:model "galileo"}}})))))

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
          {:answer {wkk/service          [:llm/openai :provider/openai]
                    wkk/model-parameters {:model "galileo"}}
           :test   {wkk/service          [:llm/openai :provider/openai]
                    wkk/model-parameters {:model "AGI"}}})))))

(deftest conversation-slot-filling
  (is (match?
       [{llm.chat/role    llm.chat/system
         llm.chat/content "You are a brilliant cook."}
        {llm.chat/role    llm.chat/user
         llm.chat/content "What is a good cake?"}
        {llm.chat/role    llm.chat/assistant
         llm.chat/content "Good cake is a cake that is good."}
        {llm.chat/role    llm.chat/user
         llm.chat/content "Help me to learn the ways of a good cake."}]
       (with-redefs [complete/chat-completion (fn [ctx _] ctx)
                     openai/complete          (fn [_])]
         (chat
          [(llm.chat/speak llm.chat/system "You are a brilliant {{role}}.")
           (llm.chat/speak llm.chat/user "What is a good {{meal}}?")
           (llm.chat/speak llm.chat/assistant "Good {{meal}} is a {{meal}} that is good.")
           (llm.chat/speak llm.chat/user "Help me to learn the ways of a good {{meal}}.")]
          {:role "cook"
           :meal "cake"})))))
