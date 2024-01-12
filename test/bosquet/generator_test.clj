(ns bosquet.generator-test
  (:require
   [bosquet.llm.generator :as gen]
   [bosquet.llm.wkk :as wkk]
   [bosquet.utils :as u]
   [clojure.test :refer [deftest is]]))

(def echo-service-chat-last
  "Fake generation. Take last message and repeat it as generation output"
  (fn [_system {msg :messages}]
    {wkk/content
     {:role :assistant :content (-> msg last :content)}}))

(def echo-service-chat-first
  "Fake generation. Take first message and repeat it as generation output"
  (fn [_system {msg :messages}]
    {wkk/content
     {:role :assistant :content (-> msg first :content)}}))

(deftest chat-generation
  (is (= {:bosquet/conversation [[:system "You are a brilliant writer."]
                                 [:user (u/join-nl
                                         "Write a synopsis for the play:"
                                         "Title: Mr. O")]
                                 [:assistant "You are a brilliant writer."]
                                 [:user "Now write a critique of the above synopsis:"]
                                 [:assistant "Now write a critique of the above synopsis:"]]
          :bosquet/completions  {:synopsis "You are a brilliant writer."
                                 :critique "Now write a critique of the above synopsis:"}
          :bosquet/usage {:synopsis      nil
                          :critique      nil
                          :bosquet/total {:prompt 0 :completion 0 :total 0}}}
         (gen/generate
          {:service-last  {wkk/chat-fn echo-service-chat-last}
           :service-first {wkk/chat-fn echo-service-chat-first}}
          [[:system "You are a brilliant writer."]
           [:user ["Write a synopsis for the play:"
                   "Title: {{title}}"]]
           [:assistant (gen/llm :service-first wkk/var-name :synopsis)]
           [:user "Now write a critique of the above synopsis:"]
           [:assistant (gen/llm :service-last wkk/var-name :critique)]]
          {:title "Mr. O"}))))

(deftest map-generation
  (is (= {gen/completions {:question-answer "Question: What is the distance from Moon to Io?  Answer:"
                           :answer          "!!!"
                           :self-eval       (u/join-nl
                                             "Question: What is the distance from Moon to Io?"
                                             "Answer: !!!"
                                             "Is this a correct answer?")
                           :test            "!!!"}
          gen/usage       {:answer        {:prompt 1 :completion 3 :total 4}
                           :test          {:prompt 1 :completion 3 :total 4}
                           :bosquet/total {:prompt 2 :completion 6 :total 8}}}
         (gen/generate
          {:service-const {wkk/chat-fn (fn [_ _]
                                         {wkk/content {:content "!!!" :role :assistant}
                                          wkk/usage   {:prompt 1 :completion 3 :total 4}})}}
          {:question-answer "Question: {{question}}  Answer:"
           :answer          (gen/llm :service-const wkk/context :question-answer)
           :self-eval       ["Question: {{question}}"
                             "Answer: {{answer}}"
                             "Is this a correct answer?"]
           :test            (gen/llm :service-const wkk/context :self-eval)}
          {:question "What is the distance from Moon to Io?"}))))

(deftest fail-generation
  (is (= {gen/completions {:in  "How are you?"
                           :out nil}
          gen/usage       {}}
         (gen/generate
          {:in  "How are you?"
           :out (gen/llm :non-existing-service wkk/context :in)}
          {}))))

(deftest appending-gen-instruction
  (is (= {gen/default-template-prompt     "What is the distance from Moon to Io?"
          gen/default-template-completion {wkk/service wkk/openai
                                           wkk/context gen/default-template-prompt}}
         (gen/append-generation-instruction
          "What is the distance from Moon to Io?"))))

(deftest usage-aggregation
  (is (= {:total 15 :completion 12 :prompt 3}
         (gen/total-usage
          {:x {:total 10 :completion 8 :prompt 2}
           :y {:total 5 :completion 4 :prompt 1}}))))
