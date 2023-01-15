(ns bosquet.generator
  (:require
    [clojure.string :as string]
    [com.wsscode.pathom3.connect.indexes :as pci]
    [com.wsscode.pathom3.connect.operation :as pco]
    [com.wsscode.pathom3.interface.smart-map :as psm]
    [bosquet.template :as template]))

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
    (let [prompt     (string/replace-first text match "")
          completion (call-generation-fn fun prompt config)]
      [(str prompt completion) completion])
    [text ""]))

(defn complete-template
  "Fill in `template` `slots` with Selmer and call generation function
  (if present) to complete the text"
  ([template slots config]
   (generation-slot->completion
     (template/fill-slots template slots)
     config))
  ([template slots] (complete-template template slots nil)))

(defn completed-key [str-key] (keyword (str str-key "-completed")))

(defn completion-key [str-key] (keyword (str str-key "-completion")))

(defn output-keys [k template]
  (let [str-k (str (.-sym k))
        completed-k (completed-key str-k)
        completion-k (completion-key str-k)]
    (if (completion-fn template)
      [k completed-k completion-k]
      [k completed-k])))

(defn- generation-resolver
  "Build dynamic resolvers figuring out what each prompt tempalte needs
  and set it as required inputs for the resolver.
  For the output check if themplate is producing generated content
  anf if so add a key for it into the output"
  [the-key template]
  (let [str-k        (str (.-sym the-key))
        completed-k  (completed-key str-k)
        completion-k (completion-key str-k)
        output       (output-keys the-key template)
        input        (vec (template/slots-required template))]
    (pco/resolver
      {::pco/op-name (symbol (keyword (str str-k "-gen")))
       ::pco/output  output
       ::pco/input   input
       ::pco/resolve
       (fn [{:generation/keys [config]} input]
         (let [[completed completion]
               (complete-template template input config)]
           (merge
             {completed-k completed
              the-key     template}
             (when-not (string/blank? completion)
               {completion-k completion}))))})))

(defn- prompt-indexes [prompts]
  (pci/register
    (mapv
      (fn [prompt-key] (generation-resolver prompt-key (prompt-key prompts)))
      (keys prompts))))

(defn all-keys [prompts]
  (vec
    (mapcat
      (fn [prompt-key] (output-keys prompt-key (prompt-key prompts)))
      (keys prompts))))

(defn complete
  "Given a map of `prompts` refering each other and
  a map of `data` to fill in template slots, generate
  text as a combination of template slot filling and AI
  generation.
  `config` holds configuration for the ai-gen call (see openai ns)
  `data-keys` are the keys to select for in the pathom resolver results"
  [prompts data config]
  (-> (prompt-indexes prompts)
    (assoc :generation/config config)
    (psm/smart-map data)
    (select-keys (all-keys prompts))))
