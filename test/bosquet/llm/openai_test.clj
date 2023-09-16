(ns bosquet.llm.openai-test
  (:require
   [clojure.test :refer [deftest is]]
   [bosquet.llm.openai :as openai]))

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
