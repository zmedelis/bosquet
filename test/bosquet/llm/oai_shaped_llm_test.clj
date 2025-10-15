(ns bosquet.llm.oai-shaped-llm-test
  (:require
   [bosquet.llm.oai-shaped-llm :as oai]
   [bosquet.llm.wkk :as wkk]
   [clojure.test :refer [deftest is]]))

(deftest prep-params-test
  (is (= {}
         (oai/prep-params {} {})))
  (is (= {:max-tokens 10}
         (oai/prep-params {:max-tokens 10})))
  (is (= {:cache      true
          :model      :gpt-10
          :max-tokens 1}
         (oai/prep-params
          {wkk/model-params {:model :gpt-10 :max-tokens 1}}
          {wkk/model-params {:model :gpt-100}
           :cache           true}))))

(deftest completion-normalization
  (let [txt       "Hello there, how may I assist you today?"
        usage-in  {:prompt_tokens 5 :completion_tokens 7 :total_tokens 12}
        usage-out {:prompt 5 :completion 7 :total 12}]
    (is (= {wkk/content         {oai/role oai/assistant oai/content txt}
            wkk/usage           usage-out
            wkk/generation-type :chat}
           (oai/->completion {:model   "gpt-3.5-turbo"
                              :object  "chat.completion"
                              :choices [{:index         0
                                         :message       {:role "assistant" :content txt}
                                         :finish_reason "stop"}]
                              :usage   usage-in})))
    (is (= {wkk/content         txt
            wkk/usage           usage-out
            wkk/generation-type :completion}
           (oai/->completion {:object  "text_completion"
                              :model   "gpt-3.5-turbo"
                              :choices [{:text          txt
                                         :index         0
                                         :logprobs      nil
                                         :finish_reason "length"}]
                              :usage   usage-in})))))
