^{:nextjournal.clerk/visibility :hide}
(ns ^:nextjournal.clerk/no-cache index
  (:require [nextjournal.clerk :as clerk]))

(clerk/html
 [:div.viewer-markdown
  [:ul
   [:li [:a.underline {:href (clerk/doc-url "notebook/user_guide/index.html")} "User guide"]]
   [:li [:a.underline {:href (clerk/doc-url "notebook/text_splitting/index.html")} "Text Chunking"]]
   [:li [:a.underline {:href (clerk/doc-url "notebook/observability/index.html")} "Observability"]]
   [:li [:a.underline {:href (clerk/doc-url "notebook/memory_prosocial_dialog/index.html")} "Long and short-term memory use"]]
   [:li [:a.underline {:href (clerk/doc-url "notebook/document_loading/index.html")} "Document Loading"]]
   [:li
    [:div "Examples"]
    [:ul
     [:li [:a.underline {:href (clerk/doc-url "notebook/examples/math_generate_code/index.html")} "Math calc with generated code"]]
     [:li [:a.underline {:href (clerk/doc-url "notebook/examples/writing_letters/index.html")} "Writing letters"]]]]
   [:li
    [:div "Paper Implementations"]
    [:ul
     [:li [:a.underline {:href (clerk/doc-url "notebook/papers/chain_of_density/index.html")} "Chain of Density"]]
     [:li [:a.underline {:href (clerk/doc-url "notebook/papers/chain_of_verification/index.html")} "Chain of Verification"]]]]
   [:li
    [:div "Presentations"]
    [:ul
     [:li [:a.underline {:href "https://clojureverse.org/t/scicloj-llm-meetup-3-llmops-with-bosquet-summary-recording/"} "2023-06-17: LLMOps with Bosquet (Scicloj)"]]
     [:li [:a.underline {:href "https://clojureverse.org/t/scicloj-llm-meetup-6-implementing-research-papers-with-bosquet-summary-recording/"} "2023-11-17: Implementing research papers with Bosquet (Scicloj)"]]
     [:li [:a.underline {:href "https://www.youtube.com/watch?v=ywlNGiD9gCg"} "Bosquet LLM command line interface and observability tools"]]]
    ]]])
