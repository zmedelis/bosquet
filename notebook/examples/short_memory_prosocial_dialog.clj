^{:nextjournal.clerk/visibility {:code :fold}}
(ns short-memory-prosocial-dialog
  {:nextjournal.clerk/toc true}
  (:require
   [bosquet.env :as env]
   [bosquet.llm.generator :as gen]
   [bosquet.memory.long-term-memory :as long-term-memory]
   [bosquet.memory.retrieval :as r]
   [bosquet.memory.simple-memory :as simple-memory]
   [clojure.string :as string]
   [helpers :as h]
   [hfds-clj.core :as hfds]
   [nextjournal.clerk :as clerk])
  (:import
   [bosquet.db.qdrant Qdrant]))

;; # Simple RAG with memory components
;;
;; This notebook will implement a simple RAG pipeline:
;;
;; 1. Memorize a large number of domain documents
;; 1. Given a user query, find a small subset of those documents that are relevant to the query
;; 1. Construct a prompt with only the relevant documents
;; 1. Run LLM generation with the constructed prompt
;;
;; RAG pipeline will be implemented using two different memory components to show how Bosquet
;; enables different modes of encoding and storing documents.
;;
;; * One is based on *Simple Memory* - a naive implementation based on in-memory atom storage and text *cosine similarity* metrics to discover relevant documents. This memory implementation is for development purposes, not intended for any realistic use.
;; * Another is a *Long Term Memory* - implementation using embeddings and [Qdrant](https://qdrant.tech/) as vector store and retrieval backend.
;;
;; ## The goal
;;
;; ⚠️ **CONTENT WARNING:** The content in the examples might be offensive.
;;
;; Let's say we are implementing a chatbot, where posting a harmful message is
;; discouraged. If an unacceptable message is posted, the assistant has to suggest
;; more socially suitable text. This can be done with a big dataset containing
;; examples of harmful messages mapped to explanations on why the message is wrong
;; as well as rephrase suggestions.
;;
;; ## Getting the dataset
;;
;; [Prosocial Dialog](https://huggingface.co/datasets/allenai/prosocial-dialog) is
;; exactly the data set for the task:
;;
;; **Model Card**
;; > ProsocialDialog is the first large-scale multi-turn English dialogue
;; > dataset to teach conversational agents to respond to problematic content
;; > following social norms. Covering diverse unethical, problematic, biased,
;; > and toxic situations, ProsocialDialog contains responses that encourage
;; > prosocial behavior, grounded in commonsense ;; social rules (i.e.,
;; > rules-of-thumb, RoTs). Created via a human-AI collaborative framework,
;; > ProsocialDialog consists of 58K dialogues, with 331K utterances, 160K
;; > unique RoTs, and 497K dialogue safety labels accompanied by free-form
;; > rationales.
;;
;; [hfds-clj](https://github.com/zmedelis/hfds-clj) is a small utility to fetch
;; and cache HuggingFace datasets. It's `load-dataset` when first called will
;; download the dataset and Subsequent runs will read it from the local cache.

(def prosocial-dialog-dataset
  (hfds/load-dataset {:dataset "allenai/prosocial-dialog"}))

;; To simplify the example and reduce a bit the amount of data, let's only use
;; the first round of the dialog, ignoring the subsequent conversation.

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

;; ## Memory setup

;; Memory has to implement at least two functions:
;; * a function to remember documents or any type of items
;; * a function to recall remembered items

;; Both simple and long-term memory implementations provide those functions.

;; ### Short-term memory

;; A short-term memory implementation provided here uses an atom to store items
;; and uses no embedding or similar memory encoding.
;; A function to pass in items to remember:

(def sm-rememberer (simple-memory/->remember))

;; And a function to recall given a query

(def sm-recaller (simple-memory/->cue-memory))

;; ### Long-term memory
;;
;; This is a more realistic memory implementation with vector DB storage
;; and embeddings to encode items.
;;
;; Both remembering and recall will require Qdrant storage.

(def storage (Qdrant. {:collection-name "prosocial"
                       :size            384}))

;; The parameters:
;; * `collection-name` - a name in Qdrant for the collection to store the data in
;; * `size` of the vector dimensions, this will depend of the embeddings in use

;; The service providing embeddings - `Ollama` in this case - is defined in `env.edn`

(def embedding (env/val :ollama))

;; As in the case of Simple Memory, two functions to do the remembering and recall.

(def ltm-rememberer (long-term-memory/->remember storage embedding))

(def ltm-recaller (long-term-memory/->cue-memory storage embedding))

;; ### Commit the Prosocial Dialogues to the memory.

;; Prosocial dialog items can now be commited to memory.
;;
;; In the case of Simple memory, it has to be done each time we boot the
;; notebook.

;; ^{:nextjournal.clerk/visibility {:result :hide}}
(sm-rememberer {} dialog-ds-subset)

;; Committing items to Long-term memory is in the comment, so as not to run it
;; each time the notebook loads. The remembered documents are stored in Qdrant.
^{:nextjournal.clerk/visibility {:result :hide}}
(comment
  (ltm-rememberer {:model :all-minilm :content :context}
                  dialog-ds-subset))

;; ## Prosocial Chat

;; Let's assume a not-so-nice message somone in not so nice mood wants to post:

^{:nextjournal.clerk/visibility {:result :hide}}
(def user-post "Got a Christmas present from a friend. Should I say something stupid?")

;; Memory recall configuration defines how memories are retrieved:
;; * *recall-function* - defines how to retrieve the memories, cue will be used to fetch memory by similarity to the query (user message)
;; * *content-similarity-threshold* - specify how similar cue and memory entries have to be to get retrieved
;; * *memory-token-limit* - specifies how many tokens to retrieve from the memory, this way we can make sure that the LLM window does not overflow if lots of memories match the recall function

;; Calls to both type of memories:

^{:nextjournal.clerk/visibility           {:result :show}
  :nextjournal.clerk/auto-expand-results? false}
(def ltm-prosocial (ltm-recaller {:model                         :all-minilm
                                  r/memory-tokens-limit          100
                                  r/memory-objects-limit         3
                                  r/memory-content               :context}
                               user-post))

^{:nextjournal.clerk/visibility           {:result :show}
  :nextjournal.clerk/auto-expand-results? false}
(def sm-prosocial (sm-recaller
                   {r/content-similarity-threshold 0.3
                    r/memory-content               :context}
                   user-post))

;; Once the relevant memory is retrieved it will be injected into the
;; conversation stream. It's up to the user how to do this. Here, I will define
;; how safety annotations and rots are to be used to generate an appropriate
;; response.
;;
^{:nextjournal.clerk/visibility {:result :hide}}
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

;; `prosocial-check` needs to be injected into the conversation stream. Note
;; that Selmer constructions and slot filling works in chat mode as well.

(def chat-msg
  [[:user user-post]
   [:user prosocial-check]
   [:assistant (gen/llm :gpt-4
                        :llm/var-name :suggestion
                        :llm/model-params {:max-tokens 300})]])

;; **Finally**, the assistant response should suggest how not to speak in two versions.
;;
;; #### One based on simple memory retrieval

(def sm-result (gen/generate chat-msg (first sm-prosocial)))

^{:nextjournal.clerk/visibility {:code :hide}}
(clerk/html
  [:div.block.p-6.bg-white.border.border-gray-200.rounded-lg.shadow.hover:bg-gray-100.dark:bg-gray-800.dark:border-gray-700.dark:hover:bg-gray-700.grid.grid-cols-1.gap-3
   [:div.font-mono [:em "Retrieved item:"]]
   [:div.font-mono (string/join " " (-> sm-prosocial first :rots))]
   [:div.font-mono [:em "Assistant:"]]
   [:div.font-mono (-> sm-result :bosquet/completions :suggestion)]])

;; #### Another based on long-term memory retrieval

(def ltm-result (gen/generate chat-msg (first ltm-prosocial)))

^{:nextjournal.clerk/visibility {:code :hide}}
(clerk/html
 [:div.block.p-6.bg-white.border.border-gray-200.rounded-lg.shadow.hover:bg-gray-100.dark:bg-gray-800.dark:border-gray-700.dark:hover:bg-gray-700.grid.grid-cols-1.gap-3
  [:div.font-mono [:em "Retrieved item:"]]
  [:div.font-mono (string/join " " (-> ltm-prosocial first :rots))]
  [:div.font-mono [:em "Assistant:"]]
  [:div.font-mono (-> ltm-result :bosquet/completions :suggestion)]])
