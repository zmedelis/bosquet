(ns bosquet.generator-test
  (:require
   [bosquet.complete :as complete]
   [bosquet.llm.chat :as llm.chat]
   [bosquet.llm.generator :refer [chat generate all-keys2]]
   [bosquet.llm :as llm]
   [bosquet.llm.openai :as openai]
   [clojure.test :refer [deftest is]]
   [matcher-combinators.matchers :as m]
   [matcher-combinators.test :refer [match?]]))

(def astronomy-prompt
  {:role            "As a brilliant {{you-are}} answer the following question."
   :question-answer "Question: {{question}}  Answer: {% gen answer %}"
   :self-eval       "{{answer}} Is this a correct answer? {% gen test %}"})

(deftest keys-to-produce
  (is (match? (m/in-any-order [:role :question :question-answer :self-eval :you-are :answer :test])
              (all-keys2 astronomy-prompt {:you-are "astronomer" :question "How far to X?"})))

  (is (match? (m/in-any-order [:role :title :genre])

              (all-keys2
               [:system "You are a {{role}}. Given the play's title and genre write synopsis."
                :user ["You sit down to write the following work."]
                :user ["Title: {{title}}"
                       "Genre: {{genre}}"]
                :user "Playwright: This is a synopsis for the above play:"]

               {:role "playwright" :title "The Tempest" :genre "comedy"})))

  (is (match? (m/in-any-order [:role :question :question-answer :self-eval :you-are :answer :test])
              (all-keys2 astronomy-prompt {:you-are "astronomer" :question "How far to X?"}))))

(def astro-service-chat
  (fn [_system {model :model}]
    {bosquet.llm.llm/content
     {:completion
      {:role :user
       :content
       (condp = model
         "galileo" "0.0017 AU"
         "hubble"  "Yes"
         (throw (ex-info (str "Unknown model: " model) {})))}}}))

(deftest generltion-with-different-models
  (is
   (match?
    {:question "What is the distance from Moon to Io?"
     :test     "Yes"
     :you-are  "astronomer"}
    (generate
     {:service-1 {llm/chat-fn astro-service-chat}}
     {:test   {llm/service          :service-1
               llm/model-params {:model "hubble"}}
      :answer {llm/service      :service-1
               llm/model-params {:model "galileo"}}}

     astronomy-prompt

     {:you-are  "astronomer"
      :question "What is the distance from Moon to Io?"}))))

(deftest fail-generation
  (is (match?
       {:question "What is the distance from Moon to Io?"
        :you-are  "astronomer"}
       (generate
        {:service-1 {llm/chat-fn astro-service-chat}}
        {:answer {llm/service      :service-1
                  llm/model-params {:model "galileo"}}
         :test   {llm/service      :service-1
                  llm/model-params {:model "AGI"}}}
        astronomy-prompt
        {:you-are  "astronomer"
         :question "What is the distance from Moon to Io?"}))))

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
