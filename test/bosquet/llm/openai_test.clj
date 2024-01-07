(ns bosquet.llm.openai-test
  (:require
   [bosquet.llm.chat :as chat]
   [bosquet.llm.wkk :as wkk]
   [bosquet.llm.openai :as openai]
   [clojure.test :refer [deftest is]]))

(defn create-chat-completion
  [prompt _params _opts]
  (str "chat:" prompt))

(defn create-completion
  [prompt _params _opts]
  (str "completion:" prompt))

(deftest complete-test
  (with-redefs [openai/create-chat-completion create-chat-completion
                openai/create-completion      create-completion]
    (is (= "completion:Fox" (openai/complete "Fox" {:model "text-ada-001"})))
    (is (= "chat:Fox" (openai/complete "Fox" {:model "gpt-4"})))))

(deftest completion-normalization
  (let [txt       "Hello there, how may I assist you today?"
        usage-in  {:prompt_tokens 5 :completion_tokens 7 :total_tokens 12}
        usage-out {:prompt 5 :completion 7 :total 12}]
    (is (= {wkk/content         {chat/role chat/assistant chat/content txt}
            wkk/usage           usage-out
            wkk/generation-type :chat}
           (openai/->completion {:model   "gpt-3.5-turbo"
                                 :object  "chat.completion"
                                 :choices [{:index         0
                                            :message       {:role "assistant" :content txt}
                                            :finish_reason "stop"}]
                                 :usage   usage-in})))
    (is (= {wkk/content         txt
            wkk/usage           usage-out
            wkk/generation-type :completion}
           (openai/->completion {:object  "text_completion"
                                 :model   "gpt-3.5-turbo"
                                 :choices [{:text          txt
                                            :index         0
                                            :logprobs      nil
                                            :finish_reason "length"}]
                                 :usage   usage-in})))))
