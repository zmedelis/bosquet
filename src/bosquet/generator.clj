(ns bosquet.generator
  (:require
    [clojure.string :as string]
    [com.wsscode.pathom3.connect.indexes :as pci]
    [com.wsscode.pathom3.connect.operation :as pco]
    [com.wsscode.pathom3.interface.smart-map :as psm]
    [bosquet.template :as template]))

(defn- call [this & that]
  (apply (resolve (symbol this)) that))

(defn- generation-slot->completion
  "Return tupe of `prompt` without the slot for gen function and
  `completion` as returned from text generation function"
  [text]
  (if-let [[match fun] (re-find #"\(\((.*?)\)\)" text)]
    (let [prompt (string/replace-first text match "")]
      [prompt (call fun prompt)])
    [text ""]))

(defn- prefix-ns
  "Add `ns` as a new namespace for a `key`"
  [ns key]
  (keyword
    (str ns (namespace key))
    (name key)))

(defn- generation-resolver [the-key template]
  (pco/resolver
    {::pco/op-name (symbol (prefix-ns "generator" the-key))
     ::pco/output  [the-key
                    #_(prefix-ns (namespace the-key) :generated-text)]
     ::pco/input   (template/slots-required template)
     ::pco/resolve
     (fn [_env input]
       (let [[prompt completion]
             (generation-slot->completion
               (template/fill-text-slots template input))]
         (merge
           {the-key (str prompt completion)}
           #_{(prefix-ns (namespace the-key) :generated-text) completion}
           (when-not (string/blank? completion)
             {(prefix-ns (namespace the-key) :generated-text) completion}))))}))

(defn- prompt-indexes [prompts]
  (pci/register
    (mapv
      (fn [prompt-key] (generation-resolver prompt-key (prompt-key prompts)))
      (keys prompts))))

(defn complete
  "Given a map of `prompts` refering each other and
  a map of `data` to fill in template slots, generate
  text as a combination of template slot filling and AI
  generation.
  `data-to-get` is a vector of keys in a template map to
  eventualy hold produced text."
  [prompts data data-to-get]
  (-> (prompt-indexes prompts)
    (psm/smart-map data)
    (select-keys data-to-get)))
