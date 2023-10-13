(ns papers.chain-of-density
  (:require [bosquet.llm.generator :as g]))


(def review (slurp "notebook/papers/review_a_man_of_two_faces.txt"))

(def cod-prompt
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

- The first summary should be long ({{LENGTH-IN-SENTENCES|default:4-4}} sentences, ~{{LENGTH-IN-WORDS|default:80}} words) yet highly non-specific, containing little information beyond the entities marked as missing.
  Use overly verbose language and fillers (e.g., \"this article discusses\") to reach ~{{LENGTH-IN-WORDS|default:80}} words.
- Make every word count: rewrite the previous summary to improve flow and make space for additional entities.
- Make space with fusion, compression, and removal of uninformative phrases like \"the article discusses\".
- The summaries should become highly dense and concise yet self-contained, i.e., easily understood without the article.
- Missing entities can appear anywhere in the new summary.
- Never drop entities from the previous summary. If space cannot be made, add fewer new entities.

Remember, use the exact same number of words for each summary. Answer in JSON. The JSON should be a list (length 5) of dictionaries whose keys are \"Missing_Entities\" and \"Denser_Summary\". "

  )

(g/generate
  cod-prompt
  {:ARTICLE review})
