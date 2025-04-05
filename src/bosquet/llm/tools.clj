(ns bosquet.llm.tools
  (:require [clojure.string :as str]
            [cheshire.core :as json]
            [bosquet.llm.wkk :as wkk]
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
                                 arguments)))) %)) ))

(defn- apply-fn [tool function]
  (let [args (first (:arglists (meta tool)))]
    (apply tool (map #(get (:arguments function) (keyword %)) args))))

(defn- apply-tool
  [tools {:keys [id function]}]
  (let [tool (select-tool-by-name tools function)]
    {:id id
     :function function
     :result (when tool
               (apply-fn tool function))} ))

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
          fn-results (->> (:tool_calls parsed-result)
                          (map #(apply-tool tools %)))
          tool-messages (tool-result-formatter engine result fn-results)
          messages (concat (vec (:messages params)) tool-messages)]
      (timbre/debug messages)
      (if (seq? tool-messages)
        (generator (-> params 
                       (dissoc wkk/tools) 
                       (assoc :messages messages)))
        result))
    result))

(comment 
  (defn ^{:desc "Get the current weather in a given location"} get-current-weather
    [^{:type "string" :desc "The city, e.g. San Francisco"} location]
    (timbre/infof "Applying get-current-weather for location %s" location)
    (case (str/lower-case location)
      "tokyo" {:location "Tokyo" :temperature "10" :unit "fahrenheit"}
      "san francisco" {:location "San Francisco" :temperature "72" :unit "fahrenheit"}
      "paris" {:location "Paris" :temperature "22" :unit "fahrenheit"}
      {:location location :temperature "unknown"}))

  (defn ^{:desc "add 'x' and 'y'"} add
    [^{:type "number" :desc "First number to add"} x
     ^{:type "number" :desc "Second number to add"} y]
    (timbre/infof "Applying add for location %s %s" x y)
    (+ (if (number? x)  x (Float/parseFloat x) )
       (if (number? y)  y (Float/parseFloat y) )))

(defn ^{:desc "subtract 'y' from 'x'"} sub
    [^{:type "number" :desc "Number to subtract from"} x
     ^{:type "number" :desc "Number to subtract"} y]
  (timbre/infof "Applying sub for location %s %s" x y)
  (- (if (number? x)  x (Float/parseFloat x) )
       (if (number? y)  y (Float/parseFloat y) )))

  (tool->function #'get-current-weather)
  (tool->function #'add))
