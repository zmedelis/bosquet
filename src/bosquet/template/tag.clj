(ns bosquet.template.tag
  (:require
   [bosquet.llm :as llm2]
   [bosquet.llm.chat :as chat]
   [bosquet.llm.llm :as llm]
   [selmer.parser :as parser]
   [taoensso.timbre :as timbre]))

(def ^:private preceding-text
  "This is where Selmer places text preceding the `gen` tag"
  :selmer/preceding-text)

(defn gen-tag
  [args {prompt     preceding-text
         service    :service
         properties :properties}]
  (let [target                    (-> args first keyword)
        llm-impl                  (get-in properties
                                          (if (contains? properties target)
                                            [target llm2/service]
                                            [llm2/service]))
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
            (timbre/error e)
            nil))]
    {(or target :bosquet/gen)
     (-> result
         llm/content
         :completion
         :content)}))

(defn add-tags []
  (parser/add-tag! :gen gen-tag))
