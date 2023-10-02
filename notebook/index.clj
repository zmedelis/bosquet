^{:nextjournal.clerk/visibility :hide}
(ns ^:nextjournal.clerk/no-cache index
  (:require [nextjournal.clerk :as clerk]))

(clerk/html
 [:div.viewer-markdown
  [:ul
   [:li
    [:a.underline {:href (clerk/doc-url "notebook/getting_started/index.html")} "Getting Started"]
    #_[:a.underline {:href (clerk/doc-url "notebook/user_guide")} "User Guide"]]]])
