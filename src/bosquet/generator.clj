(ns bosquet.generator
  (:require
    [clojure.string :as string]
    [com.wsscode.pathom3.connect.indexes :as pci]
    [com.wsscode.pathom3.connect.operation :as pco]
    [com.wsscode.pathom3.interface.smart-map :as psm]
    [bosquet.template :as template]))

(def full-prompt :completion/full-text)

(def generated-text :completion/generated-text)

(def result-keys [full-prompt generated-text])

(defn- call-generation-fn
  "Call `generation-fn` specified in prompt template with model/generation `config`"
  [generation-fn prompt config]
  ((resolve (symbol generation-fn)) prompt config))

(defn- completion-fn
  "Find call to completion function in `template`"
  [template]
  (re-find #"\(\((.*?)\)\)" template))

(defn- generation-slot->completion
  "Return tuple of `prompt` without the slot for gen function and
  `completion` as returned from text generation function"
  [text config]
  (if-let [[match fun] (completion-fn text)]
    (let [prompt (string/replace-first text match "")]
      [prompt (call-generation-fn fun prompt config)])
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
     ::pco/output  (if (completion-fn template)
                     [the-key full-prompt generated-text]
                     [the-key])
     ::pco/input   (vec (template/slots-required template))
     ::pco/resolve
     (fn [env input]
       (let [[prompt completion]
             (generation-slot->completion
               (template/fill-slots template input)
               (:generation/config env))]
         (merge
           {the-key (str prompt completion)}
           (when-not (string/blank? completion)
             {full-prompt (str prompt completion)
              generated-text completion}))))}))

(defn- prompt-indexes [prompts]
  (pci/register
    (mapv
      (fn [prompt-key] (generation-resolver prompt-key (prompt-key prompts)))
      (keys prompts))))

(defn complete
  "Given a map of `prompts` refering each other and
  a map of `data` to fill in template slots, generate
  text as a combination of template slot filling and AI
  generation."
  [prompts data config]
  (-> (prompt-indexes prompts)
    (assoc :generation/config config)
    (psm/smart-map data)
    (select-keys result-keys)))
