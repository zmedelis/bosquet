^{:nextjournal.clerk/visibility {:code :hide}}
(ns short-memory-prosocial-dialog
  (:require
   [bosquet.env :as env]
   [bosquet.llm.generator :as gen]
   [bosquet.memory.long-term-memory :as long-term-memory]
   [bosquet.memory.retrieval :as r]
   [bosquet.memory.simple-memory :as simple-memory]
   [helpers :as h]
   [hfds-clj.core :as hfds]
   [nextjournal.clerk :as clerk])
  (:import
   [bosquet.db.qdrant Qdrant]))

;; # Prosocial Dialog: Using short-term memory
;;
;; This notebook will demonstrate how to use *Short-term memory* and naive *cosine similarity* metrics to detect possibly offensive messages and suggest how to change the message into
;; a version that would not cause harm.
;;
;; **CONTENT WARNING:** The content in the examples might be offensive to some readers.
;;
;; ## Prosocial Dialog dataset
;;
;; [Hugging Face](https://huggingface.co/datasets/allenai/prosocial-dialog) hosts a dataset with conversations containing harmful content and how to correct it.
;;
;; ### Model Card
;; > ProsocialDialog is the first large-scale multi-turn English dialogue dataset to teach conversational agents to respond to problematic content following social norms.
;; > Covering diverse unethical, problematic, biased, and toxic situations, ProsocialDialog contains responses that encourage prosocial behavior, grounded in commonsense
;; social rules (i.e., rules-of-thumb, RoTs). Created via a human-AI collaborative framework, ProsocialDialog consists of 58K dialogues, with 331K utterances, 160K unique RoTs,
;; > and 497K dialogue safety labels accompanied by free-form rationales.
;;
;; ### Getting the dataset
;; Bosquet has HuggingFace dataset fetching tool (very likely that the HuggingFace datasets handler will become an independent OSS project).
;; `load-dataset` when first called will download dataset. Subsequent runs will read it from the local cache.

(def prosocial-dialog-dataset
  (hfds/load-dataset {:dataset "allenai/prosocial-dialog"}))

;; To simplify the example, let's only use the first round of the dialog, ignoring the subsequent dialog steps.

(def dialog-ds-subset
  (filter #(-> % :response_id zero?)
    prosocial-dialog-dataset))

;; Relevant data from the loaded dataset:
;; * *Context* - potentially harmful message
;; * *Response* - an example of how the assistant should respond
;; * *RoTs* - rules of thumb to guide AI in generating the response

^{::clerk/visibility {:code :hide}}
(clerk/table
  {:head ["Context" "Response" "RoTs"]
   :rows (mapv
           (fn [{:keys [context response rots]}]
             [(helpers/text-div context)
              (helpers/text-div response)
              (helpers/text-list rots)])
           dialog-ds-subset)})

;; ## Generation
;;
;; ### Memory setup
;; Short-term memory will be used for the generation. It is defined in `system.edn`. `simple-short-term-memory` is a simple implementation
;; using *in-memory atom* and *cosine similarity* for its cue mode remembering.

;; Defined memory can be retrieved from the Integrant system

;; Memory configuration defines how memories are stored and how they are retrieved:
;; * *recall-function* - defines how to retrieve the memories, cue will be used to fetch memory by similarity to the query (user message)
;; * *content-similarity-threshold* - specify how similar cue and memory entries have to be to get retrieved
;; * *memory-token-limit* - specifies how many tokens to retrieve from the memory, this way we can make sure that the LLM window does not overflow if lots of memories match the recall function


;; ### Commit the Prosocial Dialogues to the memory.

(def ltm-rememberer (long-term-memory/->remember
                     (Qdrant. {:collection-name "prosocial"
                               :size            384})
                     (env/val :ollama)))

(def sm-rememberer (simple-memory/->remember))


^{:nextjournal.clerk/visibility {:result :hide}}
(ltm-rememberer {:model :all-minilm :content :context}
                dialog-ds-subset)

(sm-rememberer {} dialog-ds-subset)

;;
;; ### Chat

;; Let's assume a not-so-nice message somone in not so nice mood wants to post:

;; ^{:nextjournal.clerk/visibility {:result :hide}}
(def user-post "Got a Christmas present from a friend. Should I say something stupid?")

(def ltm-memory (long-term-memory/->cue-memory
                 (Qdrant. {:collection-name "prosocial"
                           :size            384})
                 (env/val :ollama)))

(def sm-memory (simple-memory/->cue-memory))

;; Let's find out if anything can be remembered in Prosocial Dialog.

^{:nextjournal.clerk/visibility           {:result :show}
  :nextjournal.clerk/auto-expand-results? false}
(def ltm-prosocial (ltm-memory {:model                         :all-minilm
                                r/memory-tokens-limit          100
                                r/memory-objects-limit         3
                                r/content-similarity-threshold 0.3
                                r/memory-content               :context}
                               user-post))

^{:nextjournal.clerk/visibility           {:result :show}
  :nextjournal.clerk/auto-expand-results? false}
(def sm-prosocial (sm-memory
                   {r/content-similarity-threshold 0.4
                    r/memory-content               :context}
                   user-post))

;; Once the relevant memory is retrieved it will be injected into the conversation stream. It's up to the user how to do this. Here, I will define how safety
;; annotations and rots are to be used to generate an appropriate response.
;; ^{:nextjournal.clerk/visibility {:result :hide}}
(def prosocial-check
  ["The message above is potentialy harmfull."
   "It likely contains the following violations of socially safe conversation:"
   "{% for reason in safety_annotation_reasons %}"
   "* {{reason}}"
   "{% endfor %}"
   ""
   "Please respond with a question or suggestion urging the user to reconsider."
   "Please use the following rules of thumb when writing a reply in the form of a suggestion or question:"
   "{% for rot in rots %}"
   "* {{rot}}"
   "{% endfor %}"])

;; `prosocial-check` needs to be injected into the conversation stream. Note that Selmer constructions and slot filling works in chat mode as well.

(def chat-msg
  [[:user user-post]
   [:user prosocial-check]
   [:assistant (gen/llm :gpt-4
                        :llm/var-name :suggestion
                        :llm/model-params {:max-tokens 300})]])

;; **Finally**, the assistant response should suggest how not to speak.

(def result (gen/generate chat-msg (first ltm-prosocial)))

^{:nextjournal.clerk/visibility {:code :hide}}
(clerk/html
  [:div.block.p-6.bg-white.border.border-gray-200.rounded-lg.shadow.hover:bg-gray-100.dark:bg-gray-800.dark:border-gray-700.dark:hover:bg-gray-700.grid.grid-cols-1.gap-3
   [:div.font-mono [:em "Assistant:"]]
   [:div.font-mono (-> result :bosquet/completions :suggestion)]])
