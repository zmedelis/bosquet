(ns bosquet.generator-test
  (:require
   [bosquet.llm :as llm]
   [bosquet.llm.generator :as gen]
   [bosquet.utils :as u]
   [clojure.test :refer [deftest is]]))

(def echo-service-chat-last
  "Fake generation. Take last message and repeat it as generation output"
  (fn [_system {msg :messages :as x}]
    (prn x)
    {:bosquet.llm.llm/content {:completion {:content (-> msg last :content)}}}))

(def echo-service-chat-first
  "Fake generation. Take first message and repeat it as generation output"
  (fn [_system {msg :messages}]
    {:bosquet.llm.llm/content {:completion {:content (-> msg first :content)}}}))

(deftest chat-generation
  (is (= {:bosquet/conversation [:system "You are a brilliant writer."
                                 :user (u/join-nl
                                        "Write a synopsis for the play:"
                                        "Title: Mr. O")
                                 :assistant "You are a brilliant writer."
                                 :user "Now write a critique of the above synopsis:"
                                 :assistant "Now write a critique of the above synopsis:"]
          :bosquet/completions  {:synopsis "You are a brilliant writer."
                                 :critique "Now write a critique of the above synopsis:"}}
         (gen/generate
          {:service-last  {llm/chat-fn echo-service-chat-last}
           :service-first {llm/chat-fn echo-service-chat-first}}
          [:system "You are a brilliant writer."
           :user ["Write a synopsis for the play:"
                  "Title: {{title}}"]
           :assistant (gen/llm :service-first llm/var-name :synopsis)
           :user "Now write a critique of the above synopsis:"
           :assistant (gen/llm :service-last llm/var-name :critique)]
          {:title "Mr. O"}))))

(deftest map-generation
  (is (= {:question-answer "Question: What is the distance from Moon to Io?  Answer:"
          :answer          "!!!"
          :self-eval       (u/join-nl
                            "Question: What is the distance from Moon to Io?"
                            "Answer: !!!"
                            "Is this a correct answer?")
          :test            "!!!"}
         (gen/generate
          {:service-const {llm/chat-fn (fn [_ _] {:bosquet.llm.llm/content {:completion {:content "!!!"}}})}}
          {:question-answer "Question: {{question}}  Answer:"
           :answer          (gen/llm :service-const llm/context :question-answer)
           :self-eval       ["Question: {{question}}"
                             "Answer: {{answer}}"
                             "Is this a correct answer?"]
           :test            (gen/llm :service-const llm/context :self-eval)}
          {:question "What is the distance from Moon to Io?"}))))


(deftest fail-generation
  (is (= {:prompt     "How are you?"
          ;; TODO returning nil on error is not the best choice
          :completion nil}
         (gen/generate
          {:prompt     "How are you?"
           :completion (gen/llm :non-existing-service llm/context :prompt)}
          {}))))

(deftest appending-gen-instruction
  (is (= {:prompt     "What is the distance from Moon to Io?"
          :completion {llm/service llm/openai
                       llm/context :prompt}}
         (gen/append-generation-instruction
          "What is the distance from Moon to Io?"))))
