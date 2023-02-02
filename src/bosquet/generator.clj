(ns bosquet.generator
  (:require
    [bosquet.template :as template]
    [bosquet.template.tag :as tag]
    [com.wsscode.pathom3.connect.indexes :as pci]
    [com.wsscode.pathom3.connect.operation :as pco]
    [com.wsscode.pathom3.interface.smart-map :as psm]))

(tag/add-tags)

(defn complete-template
  "Fill in `template` `slots` with Selmer and call generation function
  (if present) to complete the text"
  [template slots] (template/fill-slots template slots))

(defn completed-key [str-key] (keyword (str str-key "-completed")))

(defn completion-key [str-key] (keyword (str str-key "-completion")))

(defn output-keys [k]
  (let [str-k (str (.-sym k))]
    [(completed-key str-k) (completion-key str-k)]))

(defn- generation-resolver
  "Build dynamic resolvers figuring out what each prompt tempalte needs
  and set it as required inputs for the resolver.
  For the output check if themplate is producing generated content
  anf if so add a key for it into the output"
  [the-key template]
  (let [str-k        (str (.-sym the-key))
        completed-k  (completed-key str-k)
        completion-k (completion-key str-k)
        output       (output-keys the-key)
        input        (vec (template/slots-required template))]
    (pco/resolver
      {::pco/op-name (symbol (keyword (str str-k "-gen")))
       ::pco/output  output
       ::pco/input   input
       ::pco/resolve
       (fn [_env input]
         (let [[completed completion] (template/fill-slots template input)]
           {completion-k completion
            completed-k completed}))})))

(defn- prompt-indexes [prompts]
  (pci/register
    (mapv
      (fn [prompt-key] (generation-resolver prompt-key (prompt-key prompts)))
      (keys prompts))))

(defn all-keys [prompts]
  (vec
    (mapcat
      (fn [prompt-key] (output-keys prompt-key))
      (keys prompts))))

(defn complete
  "Given a map of `prompts` refering each other and
  a map of `data` to fill in template slots, generate
  text as a combination of template slot filling and AI
  generation.
  `config` holds configuration for the ai-gen call (see openai ns)
  `data-keys` are the keys to select for in the pathom resolver results"
  [prompts data]
  (-> (prompt-indexes prompts)
    (psm/smart-map data)
    (select-keys (all-keys prompts))))

(comment
  (def p
    (template/read-edn (clojure.java.io/reader "resources/pp2.edn")))
  (complete p {:text-type "sentence" :for "a kid" :text "This very long stuff."})

  (clojure.pprint/pprint
    (complete
      {:role      "As a brilliant {{who-you-are}} answer the following question."
       :QnA       "{{role-completed}} {{question}} Answer: {% llm-generate var-name=answer %}"
       :self-eval "{{QnA-completed}} Is this a correct answer? {% llm-generate var-name=test%}"}
      {:who-you-are "astronomer"
       :question "What is the distance from Moon to Io?"})))
