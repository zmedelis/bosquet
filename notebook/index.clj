^{:nextjournal.clerk/visibility :hide}
(ns ^:nextjournal.clerk/no-cache index
  (:require [nextjournal.clerk :as clerk]))

(clerk/html
 [:div.viewer-markdown
  [:ul
   [:li [:a.underline {:href (clerk/doc-url "notebook/getting_started/index.html")} "Getting Started"]]
   [:li [:a.underline {:href (clerk/doc-url "notebook/configuration/index.html")} "Configuration"]]
   [:li
    [:div "Examples"]
    [:ul
     [:li [:a.underline {:href (clerk/doc-url "notebook/math_generate_code/index.html")} "Math calc with generated code"]]]]]])
