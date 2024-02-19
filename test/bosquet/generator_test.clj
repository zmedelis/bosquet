(ns bosquet.generator-test
  (:require
   [bosquet.db.cache :as cache]
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
                                 [:user (u/join-lines
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
  (is (= {gen/completions {:question-answer "Question: What is the distance from Moon to Io? Answer: !!!"
                           :answer          "!!!"
                           :self-eval       (u/join-lines
                                             "Question: What is the distance from Moon to Io? Answer: !!!"
                                             "Is this a correct answer?"
                                             "!!!")
                           :test            "!!!"}
          gen/usage       {:answer        {:prompt 1 :completion 3 :total 4}
                           :test          {:prompt 1 :completion 3 :total 4}
                           :bosquet/total {:prompt 2 :completion 6 :total 8}}}
         (gen/generate
          {:service-const {wkk/chat-fn (fn [_ _]
                                         {wkk/content {:content "!!!" :role :assistant}
                                          wkk/usage   {:prompt 1 :completion 3 :total 4}})}}
          {:question-answer "Question: {{question}} Answer: {{answer}}"
           :answer          (gen/llm :service-const)
           :self-eval       ["{{question-answer}}"
                             "Is this a correct answer?"
                             "{{test}}"]
           :test            (gen/llm :service-const)}
          {:question "What is the distance from Moon to Io?"}))))

(deftest fail-generation
  (is (= {gen/completions {:in "How are you? {{out}}" :out nil}
          gen/usage       {:out           nil
                           :bosquet/total {:prompt 0 :completion 0 :total 0}}}
         (gen/generate
          {:in  "How are you? {{out}}"
           :out (gen/llm :non-existing-service)}
          {}))))

(deftest appending-gen-instruction
  (is (= {gen/default-template-prompt     "What is the distance from Moon to Io? {{bosquet..template/completion}}"
          gen/default-template-completion (gen/default-llm)}
         (gen/append-generation-instruction
          "What is the distance from Moon to Io?"))))

(deftest chache-usage
  (let [call-counter (atom 0)
        cached-props (atom [])
        question     "What is the distance from Moon to Io?"
        generate     (fn [cache q]
                       (gen/generate
                        {:service-const {wkk/chat-fn (fn [_ props]
                                                       (swap! cached-props conj
                                                              (cache/cache-props props))
                                                       (swap! call-counter inc) {})}}
                        {:qna "Question: {{q}}  Answer: {{a}}"
                         :a   (gen/llm :service-const wkk/cache cache)}
                        {:q q}))]
    ;; cache is off
    (generate false question)
    (is (= 1 @call-counter))
    (generate false question)
    (is (= 2 @call-counter))
    ;; cache is on
    (generate true question)
    (is (= 3 @call-counter))
    (generate true question)
    (is (= 3 @call-counter))
    (generate true "What is the distance between X and Y?")
    (is (= 4 @call-counter))

    ;; clear cache
    (doseq [p @cached-props]
      (cache/evict p))))

(deftest find-var-references
  (is (= [:y :z] (gen/find-refering-templates :x {:x "aaa" :y "{{x}}" :z "{{x}} {{y}}"})))
  (is (= [:y :z] (gen/find-refering-templates :n/x {:x "aaa" :y "{{n/x}}" :z "{{n/x}} {{y}}"})))
  (is (= [:n/y :n/z] (gen/find-refering-templates :x {:x "aaa" :n/y "{{x}}" :n/z "{{x}} {{y}}"})))
  (is (= [] (gen/find-refering-templates :x {:x "aaa"}))))

(deftest ->chatml-conversion
  (is (= [{:role :user :content "Hi!"}] (gen/->chatml [[:user "Hi!"]])))
  (is (= [{:role :user :content "{\"lon\":54.1,\"lat\":50.3}"}]
         (gen/->chatml [[:user {:lon 54.1 :lat 50.3}]]))))
