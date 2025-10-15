(ns bosquet.llm.tools-test
  (:require
   [bosquet.llm.tools :refer [apply-tools tool->function]]
   [bosquet.llm.wkk :as wkk]
   [bosquet.tool.math :refer [add]]
   [bosquet.tool.weather :refer [get-current-weather]]
   [cheshire.core :as json]
   [clojure.test :refer [deftest is]]
   [jsonista.core :as j]))

 
(deftest test-tool->function
  (let [weather-spec (tool->function #'get-current-weather)]
    (is (= "function" (:type weather-spec)))
    (is (= "get-current-weather" (get-in weather-spec [:function :name])))
    (is (= "bosquet.tool.weather" (get-in weather-spec [:function :ns])))
    (is (= "Get the current weather in a given location" (get-in weather-spec [:function :description])))
    (is (= "object" (get-in weather-spec [:function :parameters :type])))
    (is (contains? (get-in weather-spec [:function :parameters :properties]) :location))
    (is (= "string" (get-in weather-spec [:function :parameters :properties :location :type])))
    (is (= "The city, e.g. Vilnius" (get-in weather-spec [:function :parameters :properties :location :description])))
    (is (= ["location"] (get-in weather-spec [:function :parameters :required])))))


(deftest test-apply-tools
  (let [available-tools [#'get-current-weather #'add]
        mock-openai-result
        {:choices
         [{:message
           {:role       "assistant"
            :content    nil
            :tool_calls [{:id   "call_abc123"
                          :type "function"
                          :function
                          {:name      "get-current-weather"
                           :arguments (json/generate-string {:location "Vilnius"})}}]}}]}

        initial-params
        {:messages [{:role "user" :content "What's the weather in Vilnius?"}]
         :model    "gpt-4"
         wkk/tools "some-tool-config-value"}

        generator-called-with (atom nil)
        mock-generator        (fn [params-for-next-call]
                                (reset! generator-called-with params-for-next-call)
                                {:final-llm-response "The weather in Vilnius is 24C"})]

    (apply-tools mock-openai-result wkk/openai initial-params available-tools mock-generator)

    (is (some? @generator-called-with) "Generator should have been called")
    (let [messages (get @generator-called-with :messages)]
      (is (= 3 (count messages)) "Should have user, assistant, and tool messages")
      (is (= {:role "user" :content "What's the weather in Vilnius?"} (first messages)))
      (is (= "assistant" (:role (second messages))))
      (is (some? (get-in (second messages) [:tool_calls 0 :function :name])))
      (is (= "tool" (:role (nth messages 2))))
      (is (= "call_abc123" (:tool_call_id (nth messages 2))))
      (is (= {:temperature "24" :unit"celcius" :location "Vilnius"}
             (-> messages (nth 2) :content (j/read-value j/keyword-keys-object-mapper))))
      (is (nil? (get @generator-called-with wkk/tools)) "tools-key should be dissoc'd"))))
