(ns bosquet.agent.wikipedia-test
  (:require
    [bosquet.agent.agent :as a]
    [bosquet.agent.wikipedia :as w]
    [clojure.test :refer [deftest is]])
  (:import
    [bosquet.agent.wikipedia Wikipedia]))

(def fox-result
  ["Fox" "Fox News" "Fox Broadcasting Company"])


(def ^:private colorado-question
  "What is the elevation range for the area that the eastern sector of the Colorado orogeny extends into?")

(def ^:private colorado-thoughts
  {colorado-question
   "Thought 1: I need to search Colorado orogeny, find the area that the eastern sector
of the Colorado orogeny extends into, then find the elevation range of the area.
Action 1: Search[Colorado orogeny]
Observation 1: The Colorado orogeny was an episode of mountain building (an orogeny) in
Colorado and surrounding areas.
Thought 2: It does not mention the eastern sector. So I need to look up eastern sector.
Action 2: Lookup[eastern sector]
Observation 2: ..."})

(def ^:private
  colorado-wiki
  {"Colorado orogeny"
  "xxxxThe Colorado orogeny was an episode of mountain building (an orogeny) in Colorado and surrounding areas. This took place from 1780 to 1650 million years ago (Mya), during the Paleoproterozoic (Statherian Period). It is recorded in the Colorado orogen, a >500-km-wide belt of oceanic arc rock that extends southward into New Mexico. The Colorado orogeny was likely part of the larger Yavapai orogeny."})

(deftest best-match-test
  (is (= "Fox" (w/best-match "Fox" fox-result)))
  (is (= "Fox" (w/best-match "Box" fox-result)))
  (is (nil? (w/best-match "Box" []))))


(deftest agent-interactions
  (with-redefs [w/produce-thoughts     (fn [query]
                                         {:thoughts (colorado-thoughts query)})
                w/extract-page-content (fn [query]
                                         (colorado-wiki query))]
    (let [agent (Wikipedia.)]
      (is (= (colorado-wiki "Colorado orogeny")
             (a/plan agent colorado-question))))))
