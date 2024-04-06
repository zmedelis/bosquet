^{:nextjournal.clerk/visibility {:code :hide}}
(ns papers.chain-of-density
  (:require
   [bosquet.llm.generator :as g]
   [bosquet.llm.wkk :as wkk]
   [nextjournal.clerk :as clerk]))

;; ## Chain of Density prompting
;;
;; Chain of Density (CoD) technique is introduced in [GPT-4 Summarization with Chain of Density Prompting](https://arxiv.org/pdf/2309.04269.pdf) paper.
;; It aims to produce high-quality and dense information text summaries.

;; > Selecting the “right” amount of information to include in a summary is a difficult task. A good summary should be detailed
;; > and entity-centric without being overly dense and hard to follow.
;;
;; ![CoD](notebook/assets/cod.png)
;;
;; CoD constructs a prompt that iteratively adds not yet summarized entities to the summary while keeping the overall summary length constant.
;; As it goes through the iterations, it produces increasingly dense summaries. Initial summaries are too sparse, while the final ones are
;; usually too dense. Second and third versions being the best ones.

;; Another nice feature of the CoD prompt is that it keeps all the summary iteration prompts alongside key entities added to the summary in
;; a generated JSON output. This allows us to inspect the intermediate steps of the summary generation, see how the summary is evolving,
;; and choose the best one.
;;
;; **For the impatient** - to see the Cot summarization output, jump to the end of this notebook to see the generated summaries.
;;
;; ## Implementation
;;
;; Let's take a Wikipedia article on [2023 Herat earthquakes](https://en.wikipedia.org/wiki/2023_Herat_earthquakes) and generate a summary of it using the CoD technique.

(def article (slurp "notebook/papers/2023_Herat_earthquakes.txt"))

;; Prompt taken from the paper. Note its structure:
;; - Instructing to proceed in iterations
;; - Each iteration asks to produce a denier summary based on missing entities
;; - Guidelines instructing to proceed with summary generation preserving the length and already conveyed information
;; - Output shape to include missing entities and summary
;;
;; *Bosquet* allows adding some extra configuration to the prompt.
;; - `LENGTH-IN-SENTENCES` and `LENGTH-IN-WORDS` allows to control the lenght of the summary, *Selmer* templating allows to add the defaults for those values.
;; - `FORMAT` to control the output format, defaults to `JSON` (more on that later)

(def cod-prompt
  [[:user
    "Article: {{ ARTICLE }}

You will generate increasingly concise, entity-dense summaries of the above article.

Repeat the following 2 steps 5 times.

Step 1. Identify 1-3 informative entities (\";\" delimited) from the article which are missing from the previously generated summary.

Step 2. Write a new, denser summary of identical length which covers every entity and detail from the previous summary plus the missing entities.

A missing entity is:
- relevant to the main story,
- specific yet concise (5 words or fewer),
- novel (not in the previous summary),
- faithful (present in the article),
- anywhere (can be located anywhere in the article).

Guidelines:

- The first summary should be long ({{LENGTH-IN-SENTENCES|default:3-4}} sentences, ~{{LENGTH-IN-WORDS|default:80}} words) yet highly non-specific, containing little information beyond the entities marked as missing.
  Use overly verbose language and fillers (e.g., \"this article discusses\") to reach ~{{LENGTH-IN-WORDS|default:80}} words.
- Make every word count: rewrite the previous summary to improve flow and make space for additional entities.
- Make space with fusion, compression, and removal of uninformative phrases like \"the article discusses\".
- The summaries should become highly dense and concise yet self-contained, i.e., easily understood without the article.
- Missing entities can appear anywhere in the new summary.
- Never drop entities from the previous summary. If space cannot be made, add fewer new entities.

Remember, use the exact same number of words for each summary. Answer in {{FORMAT|default:JSON}}. The {{FORMAT|default:JSON}} should be a list (length 5) of dictionaries whose keys
are \"Missing-Entities\" and \"Denser-Summary\".

{{sum-gen}}"]
   [:assistant (g/llm :gpt-4
                      wkk/var-name :sum-gen
                      wkk/output-format :json)]])

;;
;; With that set a call to generation (see *Getting Started* and *Configuration* notebooks for more details on how generation works) can be made.
;; Note the `output-format` and `FORMAT` parameters:
;; - the `FORMAT` will be used to fill in the string value in the template;
;; - the `output-format` is a *Bosquet* parameter that will initiate result postprocessing and coerce the result into the specified format. Currently supported formats: EDN, JSON, and plain text.
;;

^{:nextjournal.clerk/visibility {:result :hide}}
(def result (g/generate cod-prompt {:ARTICLE article :FORMAT  "JSON"}))

;; CoT - as instructed - produces a list of 5 summaries, each summary is a map with `Missing-Entities` and `Denser-Summary` keys. Authors of the paper did human evaluation
;; of the produced summaries and found that humans usualy prefer 2-3rd summaries.

^{:nextjournal.clerk/visibility {:code :hide}}
(clerk/html
  (vec
    (cons
      :div.font-mono
      (map-indexed
        (fn [idx {:strs [Missing-Entities Denser-Summary]}]
          [:div.block.p-6.bg-white.border.border-gray-200.rounded-lg.shadow.hover:bg-gray-100.dark:bg-gray-800.dark:border-gray-700.dark:hover:bg-gray-700.grid.grid-cols-1.gap-3
           [:div.flex
            [:div.flex-none.w-32.mr-4 [:em "Step:"]]
            [:div (inc idx)]]
           [:div.flex
            [:div.flex-none.w-32.mr-4 [:em "Missing Entities:"]]
            [:div Missing-Entities]]
           [:div.flex
            [:div.flex-none.w-32.mr-4 [:em "Denser Summary:"]]
            [:div Denser-Summary]]])
        (get-in result [g/completions :sum-gen])))))
