(ns bosquet.llm.tools
  (:require
   [bosquet.llm.wkk :as wkk]
   [cheshire.core :as json]
   [taoensso.timbre :as timbre]))

(defn tool->function [tool-var]
  (let [fn-meta (meta tool-var)
        args (into {}
                   (map
                    (fn [arg]
                      (let [arg-meta (meta arg)]
                        [(keyword arg) {:type (:type arg-meta)
                                        :description (:desc arg-meta)}]))
                    (first (:arglists (meta tool-var)))))]
    {:type "function"
     :function {:name (name (:name fn-meta))
                :ns (str (:ns fn-meta))
                :description (:desc fn-meta)
                :parameters {:type "object"
                             :properties args
                             :required (map name (first (:arglists fn-meta)))}}}))

(defn- select-tool-by-name [tools function]
  (first (filter #(= (:name function) (name (:name (meta %)))) tools)))

(defn- parse-arguments [model-engine result]
  (let [result (condp = model-engine
                 wkk/ollama (:message result)
                 wkk/openai (-> result :choices first :message))]
    (update result :tool_calls
            #(map (fn [tool-call]
                    (update-in tool-call [:function :arguments]
                               (fn [arguments]
                                 (if (string? arguments)
                                   (json/parse-string arguments true)
                                   arguments)))) %))))

(defn- apply-fn [tool function]
  (let [args (first (:arglists (meta tool)))]
    (apply tool (map #(get (:arguments function) (keyword %)) args))))

(defn- apply-tool
  [tools {:keys [id function]}]
  (let [tool (select-tool-by-name tools function)]
    {:id id
     :function function
     :result (when tool
               (apply-fn tool function))}))

(defn- tool-result-formatter
  [model-engine result tool-results]
  (let [tool-results (map #(condp = model-engine
                             wkk/ollama {:role "tool" :content (json/generate-string (:result %))}
                             wkk/openai {:role "tool" :content (json/generate-string (:result %)) :tool_call_id (:id %)}) tool-results)]
    (condp = model-engine
      wkk/ollama tool-results
      wkk/openai (concat [{:role "assistant" :tool_calls (-> result :choices first :message :tool_calls)}] tool-results))))

(defn apply-tools
  [result engine params tools generator]
  (timbre/infof "Applying tools %d for engine %s" (count tools) engine)
  (if (not-empty tools)
    (let [parsed-result (parse-arguments engine result)
          fn-results    (->> (:tool_calls parsed-result)
                             (map #(apply-tool tools %)))
          tool-messages (tool-result-formatter engine result fn-results)
          messages      (concat (vec (:messages params)) tool-messages)]
      (timbre/debug messages)
      (if (seq? tool-messages)
        (generator (-> params
                       (dissoc wkk/tools)
                       (assoc :messages messages)))
        result))
    result))

(comment
  ;; Example tool registration
  (require '[bosquet.tool.math :refer [add]]
           '[bosquet.tool.weather :refer [get-current-weather]])
  (tool->function #'get-current-weather)
  (tool->function #'add))
