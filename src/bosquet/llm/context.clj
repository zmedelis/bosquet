(ns bosquet.llm.context
  (:require
   [bosquet.converter :as converter]
   [bosquet.llm :as llm]
   [bosquet.template.read :as template]
   [clojure.string :as string]
   [com.wsscode.pathom3.connect.indexes :as pci]
   [com.wsscode.pathom3.connect.operation :as pco]
   [com.wsscode.pathom3.interface.smart-map :as psm]
   [selmer.parser :as selmer]
   [taoensso.timbre :as timbre]))

(defn- ->id
  [idx role]
  (format "%s-%s" idx (name role)))

(defn ->chatml [messages]
  (map
   (fn [[role content]] {:role role :content content})
   messages))

(defn call-gen [llm-config properties messages]
  (try
    (let [llm-impl       (llm/service properties)
          format         (partial converter/coerce (llm/output-format properties))
          model-params   (llm/model-params properties)
          chat-fn        (get-in llm-config [llm-impl llm/chat-fn])
          service-config (dissoc (llm-impl llm-config) llm/gen-fn llm/chat-fn)
          messages       (->chatml messages)
          result         (chat-fn service-config (assoc model-params :messages messages))]
      (format
       (get-in result
               [:bosquet.llm.llm/content :completion :content])))
    (catch Exception e
      (timbre/error e))))

(defn- generate [llm-config properties ctx]
  (if (map? properties)
    (call-gen llm-config properties ctx)
    (timbre/warnf ":assistant instruction does not contain AI gen function spec")))

(defn- join
  [content]
  (if (coll? content) (string/join "\n" content) content))

(defn chat
  [llm-config messages vars-map]
  (loop [[role content & messages] messages
         processed-messages          []
         ctx                         vars-map]
    (if (nil? role)
      {:bosquet/content processed-messages
       :bosquet/vars    ctx}
      (if (= :assistant role)
        (let [gen-result (generate llm-config content processed-messages)
              var-name   (llm/var-name content)]
          (recur messages
                 (conj processed-messages [role gen-result])
                 (assoc ctx var-name gen-result)))
        (let [tpl-result (first (template/render (join content) vars-map))]
          (recur messages
                 (conj processed-messages [role tpl-result])
                 ctx))))))

(defn- generation-resolver
  [llm-config message-key message-content]
  (if (map? message-content)
    (pco/resolver
     {::pco/op-name (-> message-key .-sym (str "-ai-gen") keyword symbol)
      ::pco/output  [message-key]
      ::pco/input   [(:context message-content)]
      ::pco/resolve
      (fn [{entry-tree :com.wsscode.pathom3.entity-tree/entity-tree*} _input]
        (try
          (let [full-text (get @entry-tree (:context message-content))
                result    (generate llm-config message-content [[:user full-text]])]
            {message-key result})
          (catch Exception e
            (timbre/error e))))})
    ;; TEMPLATE
    (let [message-content (join message-content)]
      (pco/resolver
       {::pco/op-name (-> message-key .-sym (str "-template") keyword symbol)
        ::pco/output  [message-key]
        ::pco/input   (vec (selmer/known-variables message-content))
        ::pco/resolve
        (fn [{entry-tree :com.wsscode.pathom3.entity-tree/entity-tree*} _input]
          {message-key (first (template/render message-content @entry-tree))})}))))

(defn- prompt-indexes [llm-config messages]
  (pci/register
   (mapv
    (fn [prompt-key]
      (generation-resolver llm-config
                           prompt-key
                           (get messages prompt-key)))
    (keys messages))))

(defn complete
  [llm-config messages vars-map]
  (let [vars-map (merge vars-map {:bosquet/full-text (atom "")})
        indexes (prompt-indexes llm-config messages)
        sm (psm/smart-map indexes vars-map)]
    (select-keys sm (keys messages))))

(comment
  (def play-c
    [:system "You are a playwright. Given the play's title and genre write synopsis."
     :user "Playwright, wirite a synopsis for the following play."
     :user ["Title: {{title}}"
            "Genre: {{genre}}"]
     :assistant {llm/service      llm/openai
                 llm/var-name     :play
                 :cache           true
                 llm/model-params {:model :gpt-3.5-turbo}}
     :user "Review from a Nice City Times critic of the above synopsis:"
     :assistant {llm/service      llm/openai
                 llm/var-name     :review
                 :cache           true
                 llm/model-params {:model :gpt-3.5-turbo}}])

  (def play-g
    {:role      "You are a playwright. Given the play's title and genre write synopsis."
     :task      "Playwright, write a synopsis for the play."
     :write     ["{{role}}"
                 "{{task}}"
                 ""
                 "Title: {{title}}"
                 "Genre: {{genre}}"]
     :play      {llm/service llm/openai
                 :context    :write}
     :criticize ["{{role}}"
                 "Review from a Nice City Times critic of the following play:"
                 "Title: {{title}}"
                 "Genre: {{genre}}"
                 "{{play}}"]
     :review    {llm/service llm/openai
                 :context    :criticize}})

  (tap> (complete llm/default-services play-g {:title "City of Shadows" :genre "crime"}))

  (tap> (chat llm/default-services play-c {:title "Orbital drift" :genre "Sci-Fi"}))
  #__)
