(ns bosquet.template.tag
  (:require
   [bosquet.converter :as converter]
   [bosquet.llm :as llm2]
   [bosquet.llm.chat :as chat]
   [bosquet.llm.llm :as llm]
   [selmer.parser :as parser]
   [taoensso.timbre :as timbre]))

(def ^:private preceding-text
  "This is where Selmer places text preceding the `gen` tag"
  :selmer/preceding-text)

(defn- get-prop
  "Get property from `properties` map. The map can be speified with diferent
  props for each generation `target`. Like

  `{:answer {:prop1 val1} :question {:prop1 val2}}`

  Or as a flat map of global config
  `{:prop1 val1 :prop2 val2}`"
  [properties target key]
  (get-in properties
          (if (contains? properties target)
            [target key]
            [key])))

(defn gen-tag
  [args {prompt     preceding-text
         service    :service
         properties :properties}]
  (let [target                    (-> args first keyword)
        llm-impl                  (get-prop properties target llm2/service)
        format                    (get-prop properties target llm2/output-format)
        ;; TODO suspicious get-in
        model-params              (dissoc (get-in properties [target llm2/model-params])
                                          llm2/service)
        {:llm/keys [chat-fn
                    complete-fn]} (llm-impl service)
        service-config            (dissoc
                                   (llm-impl service)
                                   llm2/gen-fn
                                   llm2/chat-fn)
        result
        (try
          (chat-fn service-config
                   (assoc model-params :messages (chat/converse chat/user prompt)))
          (catch Exception e
            (timbre/error e)))]

    (timbre/infof "Finished generating for '%s'. Coerce to - %s" target (if format format "N/A"))
    {(or target :bosquet/gen)
     (-> result
         llm/content
         :completion
         :content
         (converter/coerce format))}))

(defn add-tags []
  (parser/add-tag! :gen gen-tag))
