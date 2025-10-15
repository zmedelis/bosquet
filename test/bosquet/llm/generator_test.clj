(ns bosquet.llm.generator-test
  (:require
   [matcher-combinators.test]
   [bosquet.db.cache :as cache]
   [bosquet.env :as env]
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
  (with-redefs [env/config {:service-last  {:chat-fn echo-service-chat-last}
                            :service-first {:chat-fn echo-service-chat-first}}]
    (let [{:bosquet/keys [conversation completions usage time]}
          (gen/generate
           [[:system "You are a brilliant writer."]
            [:user ["Write a synopsis for the play:"
                    "Title: {{title}}"]]
            [:assistant (gen/llm :service-first wkk/var-name :synopsis)]
            [:user "Now write a critique of the above synopsis:"]
            [:assistant (gen/llm :service-last wkk/var-name :critique)]]
           {:title "Mr. O"})]
      (is (number? time))
      (is (= [[:system "You are a brilliant writer."]
              [:user (u/join-lines
                      "Write a synopsis for the play:"
                      "Title: Mr. O")]
              [:assistant "You are a brilliant writer."]
              [:user "Now write a critique of the above synopsis:"]
              [:assistant "Now write a critique of the above synopsis:"]]
             conversation))
      (is (= {:synopsis "You are a brilliant writer."
              :critique "Now write a critique of the above synopsis:"}
             completions))
      (is (= {:synopsis      nil
              :critique      nil
              :bosquet/total {:prompt 0 :completion 0 :total 0}}
             usage)))))

(deftest map-generation
  (with-redefs [env/config {:service-const
                            {:chat-fn (fn [_ _]
                                        {wkk/content {:content "!!!" :role :assistant}
                                         wkk/usage   {:prompt 1 :completion 3 :total 4}})}}]
    (let [{:bosquet/keys [completions usage]}
          (gen/generate
           {:question-answer "Question: {{question}} Answer: {{answer}}"
            :answer          (gen/llm :service-const)
            :self-eval       ["{{question-answer}}"
                              "Is this a correct answer?"
                              "{{test}}"]
            :test            (gen/llm :service-const)}
           {:question "What is the distance from Moon to Io?"})]

      (is (= {:question-answer "Question: What is the distance from Moon to Io? Answer: !!!"
              :question        "What is the distance from Moon to Io?"
              :answer          "!!!"
              :self-eval       (u/join-lines
                                "Question: What is the distance from Moon to Io? Answer: !!!"
                                "Is this a correct answer?"
                                "!!!")
              :test            "!!!"}
             completions))
      (is (= {:answer        {:prompt 1 :completion 3 :total 4}
              :test          {:prompt 1 :completion 3 :total 4}
              :bosquet/total {:prompt 2 :completion 6 :total 8}}
             usage)))))

(deftest fail-generation
  (is (match?
       {gen/completions {:in "How are you? {{out}}" :out nil}
        gen/usage       {:out           nil
                         :bosquet/total {:prompt 0 :completion 0 :total 0}}}
       (gen/generate
        {:in  "How are you? {{out}}"
         :out (gen/llm :non-existing-service)}
        {}))))

(deftest appending-gen-instruction
  (is (= {gen/default-template-prompt     "What is the distance from Moon to Io? {{bosquet..template/completion}}"
          gen/default-template-completion (env/default-service)}
         (gen/append-generation-instruction
          "What is the distance from Moon to Io?"))))

(deftest chache-usage
  (let [call-counter (atom 0)
        cached-props (atom [])
        question     "What is the distance from Moon to Io?"
        env-config {:service-const
                    {:chat-fn (fn [_ props]
                                (swap! cached-props conj props)
                                (swap! call-counter inc) {})}}
        generate     (fn [cache q]
                       (gen/generate
                        {:qna "Question: {{q}}  Answer: {{a}}"
                         :a   (gen/llm :service-const wkk/cache cache)}
                        {:q q}))]
    (with-redefs [env/config env-config]
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
        (cache/evict p)))))

(deftest find-var-references
  (is (= [:y :z] (gen/find-refering-templates :x {:x "aaa" :y "{{x}}" :z "{{x}} {{y}}"})))
  (is (= [:y :z] (gen/find-refering-templates :n/x {:x "aaa" :y "{{n/x}}" :z "{{n/x}} {{y}}"})))
  (is (= [:n/y :n/z] (gen/find-refering-templates :x {:x "aaa" :n/y "{{x}}" :n/z "{{x}} {{y}}"})))
  (is (= [] (gen/find-refering-templates :x {:x "aaa"}))))

(deftest ->chatml-conversion
  (is (= [{:role :user :content "Hi!"}] (gen/->chatml [[:user "Hi!"]])))
  (is (= [{:role :user :content "{\"lon\":54.1,\"lat\":50.3}"}]
         (gen/->chatml [[:user {:lon 54.1 :lat 50.3}]]))))

(deftest llm-spec-construction
  (is (= {wkk/service :openai} (gen/llm :openai)))
  (is (= {wkk/model-params {:model :command} wkk/service :cohere}
         (gen/llm :command))))

(deftest slot-filling
  (is (= "3 + 1 = 4"
         (get-in
          (gen/generate
           {:z "{{y}} + {{x}} = {{a}}" :a 4} {:x 1 :y 3})
          [gen/completions :z]))))

(deftest run-node-function-test
  (is (= 3 (gen/run-node-function
            {wkk/fun-impl (fn [x y] (+ x y))
             wkk/fun-args '[x y]}
            {:x 1 :y 2}))))
