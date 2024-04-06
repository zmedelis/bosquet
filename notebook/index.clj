^{:nextjournal.clerk/visibility :hide}
(ns ^:nextjournal.clerk/no-cache index
  (:require [nextjournal.clerk :as clerk]))

(clerk/html
 [:div.viewer-markdown
  [:ul
   [:li [:a.underline {:href (clerk/doc-url "notebook/user_guide/index.html")} "User guide"]]
   [:li [:a.underline {:href (clerk/doc-url "notebook/observability/index.html")} "Observability"]]
   [:li [:a.underline {:href (clerk/doc-url "notebook/memory_prosocial_dialog/index.html")} "Long and short-term memory use"]]
   [:li [:a.underline {:href (clerk/doc-url "notebook/text_splitting/index.html")} "Text Chunking"]]
   [:li [:a.underline {:href (clerk/doc-url "notebook/document_loading/index.html")} "Document Loading"]]
   [:li [:a.underline {:href (clerk/doc-url "notebook/using_llms/index.html")} "Using LLMs"]]
   [:li
    [:div "Examples"]
    [:ul
     [:li [:a.underline {:href (clerk/doc-url "notebook/examples/math_generate_code/index.html")} "Math calc with generated code"]]]]
   [:li
    [:div "Paper Implementations"]
    [:ul
     [:li [:a.underline {:href (clerk/doc-url "notebook/papers/chain_of_density/index.html")} "Chain of Density"]]
     [:li [:a.underline {:href (clerk/doc-url "notebook/papers/chain_of_verification/index.html")} "Chain of Verification"]]]]]])
