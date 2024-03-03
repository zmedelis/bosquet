(ns bosquet.llm
  (:require
   [bosquet.env :as env]
   [bosquet.llm.cohere :as cohere]
   [bosquet.llm.lmstudio :as lmstudio]
   [bosquet.llm.mistral :as mistral]
   [bosquet.llm.openai :as openai]
   [bosquet.llm.wkk :as wkk]))

(def default-services
  {wkk/lmstudio (merge (env/val wkk/lmstudio)
                       {wkk/complete-fn lmstudio/complete
                        wkk/chat-fn     lmstudio/chat})
   wkk/openai   (merge (env/val wkk/openai)
                       {wkk/complete-fn openai/complete
                        wkk/chat-fn     openai/chat})
   wkk/cohere   (merge (env/val wkk/cohere)
                       {wkk/complete-fn cohere/complete
                        wkk/chat-fn     cohere/chat})
   wkk/mistral  (merge (env/val wkk/mistral)
                       {wkk/complete-fn mistral/complete
                        wkk/chat-fn     mistral/chat})})

(def model-providers
  {:babbage-002               wkk/openai
   :davinci-002               wkk/openai
   :gpt-3.5                   wkk/openai
   :gpt-3.5-turbo             wkk/openai
   :gpt-3.5-turbo-0125        wkk/openai
   :gpt-3.5-turbo-0301        wkk/openai
   :gpt-3.5-turbo-0613        wkk/openai
   :gpt-3.5-turbo-1106        wkk/openai
   :gpt-3.5-turbo-16k-0613    wkk/openai
   :gpt-3.5-turbo-instruct    wkk/openai
   :gpt-4                     wkk/openai
   :gpt-4-0125-preview        wkk/openai
   :gpt-4-1106-preview        wkk/openai
   :gpt-4-1106-vision-preview wkk/openai
   :gpt-4-32k                 wkk/openai
   :text-embedding-3-large    wkk/openai
   :text-embedding-3-small    wkk/openai
   :text-embedding-ada-002    wkk/openai
   :open-mistral-7b           wkk/mistral
   :open-mixtral-8x7b         wkk/mistral
   :mistral-small-latest      wkk/mistral
   :mistral-medium-latest     wkk/mistral
   :mistral-large-latest      wkk/mistral
   :mistral-small             wkk/mistral
   :mistral-medium            wkk/mistral
   :mistral-large             wkk/mistral
   :command                   wkk/cohere
   :command-light             wkk/cohere
   :command-light-nightly     wkk/cohere})
