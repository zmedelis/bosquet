^{:nextjournal.clerk/visibility :hide}
(ns ^:nextjournal.clerk/no-cache index
  (:require [nextjournal.clerk :as clerk]))

(clerk/html
 [:div.viewer-markdown
  [:ul
   [:li [:a.underline {:href (clerk/doc-url "notebook/getting_started/index.html")} "Getting Started"]]
   [:li [:a.underline {:href (clerk/doc-url "notebook/configuration/index.html")} "Configuration"]]
   [:li [:a.underline {:href (clerk/doc-url "notebook/observability/index.html")} "Observability"]]
   [:li [:a.underline {:href (clerk/doc-url "notebook/text_splitting/index.html")} "Text Chunking"]]
   [:li [:a.underline {:href (clerk/doc-url "notebook/document_loading/index.html")} "Document Loading"]]
   [:li [:a.underline {:href (clerk/doc-url "notebook/using_llms/index.html")} "Using LLMs"]]
   [:li
    [:div "Examples"]
    [:ul
     [:li [:a.underline {:href (clerk/doc-url "notebook/math_generate_code/index.html")} "Math calc with generated code"]]
     [:li [:a.underline {:href (clerk/doc-url "notebook/examples/short_memory_prosocial_dialog/index.html")} "Prosocial Dialog with short-term memory"]]]]
   [:li
    [:div "Paper Implementations"]
    [:ul
     [:li [:a.underline {:href (clerk/doc-url "notebook/papers/chain_of_density/index.html")} "Chain of Density"]]
     [:li [:a.underline {:href (clerk/doc-url "notebook/papers/chain_of_verification/index.html")} "Chain of Verification"]]]]]])
