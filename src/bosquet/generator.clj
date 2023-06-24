(ns bosquet.generator
  (:require
    [taoensso.timbre :as timbre]
    [bosquet.template.read :as template]
    [bosquet.template.tag :as tag]
    [com.wsscode.pathom3.connect.indexes :as pci]
    [com.wsscode.pathom3.connect.operation :as pco]
    [com.wsscode.pathom3.interface.smart-map :as psm]))

(tag/add-tags)

(defn complete-template
  "Fill in `template` `slots` with Selmer and call generation function
  (if present) to complete the text"
  [template slots model-opts] 
  (template/fill-slots
   template    
   (assoc slots 
          :opts {:complete-template-key model-opts}
          :the-key :complete-template-key
          )))

(defn output-keys [k template]
  (vec (concat [k] (template/generation-vars template))))

(defn- generation-resolver
  "Build dynamic resolvers figuring out what each prompt tempalte needs
  and set it as required inputs for the resolver.
  For the output check if themplate is producing generated content
  anf if so add a key for it into the output"
  [the-key template model-opts]
  (let [str-k        (str (.-sym the-key))
        input        (vec (template/slots-required template))
        output       (into input (output-keys the-key template))]
    (timbre/info "Resolver: " the-key)
    (timbre/info "  Input: " input)
    (timbre/info "  Output: " output)
    (pco/resolver
      {::pco/op-name (symbol (keyword (str str-k "-gen")))
       ::pco/output  output
       ::pco/input   input
       ::pco/resolve
       (fn [_env input]
         (timbre/info "Resolving: " the-key)
         (let [[completed completion] (template/fill-slots template (assoc input 
                                                                           :opts model-opts
                                                                           :the-key the-key
                                                                           ))]
           (merge
             {the-key completed}
             completion
             input)))})))

(defn- prompt-indexes [prompts opts]
  (pci/register
    (mapv
      (fn [prompt-key] (generation-resolver prompt-key (prompt-key prompts) opts))
      (keys prompts))))

(defn all-keys
  "Produce a list of all the data keys that will come out of the Pathom processing.
  Whatever is refered in `prompts` and comes in via input `data`"
  [prompts data]
  (into (vec (keys data))
    (mapcat
      (fn [prompt-key]
        (output-keys prompt-key (get prompts prompt-key)))
      (keys prompts))))

(defn complete
  "Given a `prompt-palette` and a map of `data` to fill in template slots,
  generate text as a combination of template slot filling and AI generation.

  `entry-prompts` are the keys to the `prompt-palette` indicating where to start
  the generation process.

  When not provided, all keys in `prompt-palette` are used.
  With big prompt palettes, this can be a problem, because multiple unrelated
  prompts can be invoked"
  ([prompt-palette data]
   (complete prompt-palette data nil {}))
  ([prompt-palette data entry-prompt-keys opts]
   (let [entry-prompts   (if (empty? entry-prompt-keys) (keys prompt-palette) entry-prompt-keys)
         extraction-keys (all-keys (select-keys prompt-palette entry-prompts) data)]
     (timbre/info "Resolving keys: " extraction-keys)
     (-> (prompt-indexes prompt-palette opts)
         (psm/smart-map data)
         (select-keys extraction-keys)))))

(comment
  (complete
   {:role            "As a brilliant {{you-are}} answer the following question."
    :question        "What is the distance between Io and Europa?"
    :question-answer "Question: {{question}}  Answer: {% llm-generate var-name=answer %}"
    :self-eval       "{{answer}} Is this a correct answer? {% llm-generate var-name=test model=text-curie-001 %}"}
   {:you-are  "astronomer"
    :question "What is the distance from Moon to Io?"}
   [:question-answer :self-eval]))
