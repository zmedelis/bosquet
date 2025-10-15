(ns bosquet.mcp.stdio-transport
  (:require [clojure.java.io :as io]
            [jsonista.core :as json]
            [taoensso.timbre :as timbre]
            [bosquet.mcp.transport :refer [MCPTransport]])
  (:import [java.io BufferedReader BufferedWriter]))

(def mapper (json/object-mapper {:decode-key-fn true}))

(defn- start-process
  "Start a subprocess for stdio communication"
  [{:keys [command args env]}]
  (let [pb (ProcessBuilder. (into-array String (cons command args)))]
    (when env
      (let [process-env (.environment pb)]
        (doseq [[k v] env]
          (.put process-env (str k) (str v)))))
    (.start pb)))

(defrecord StdioTransport [process]
  MCPTransport
  (send-request [_ method params]
    (let [request                {:jsonrpc "2.0"
                                  :id      (System/currentTimeMillis)
                                  :method  method
                                  :params  (or params {})}
          ^BufferedWriter writer (io/writer (.getOutputStream process))
          ^BufferedReader reader (io/reader (.getInputStream process))]

      (let [request-json (json/write-value-as-string request)]
        (timbre/debug "STDIO →" request-json)
        (.write writer request-json)
        (.newLine writer)
        (.flush writer))

      (let [response-json (.readLine reader)
            response      (json/read-value response-json mapper)]
        (timbre/debug "STDIO ←" response-json)
        response)))

  (send-notification [_ method params]
    (let [notification           {:jsonrpc "2.0"
                                  :method  method
                                  :params  (or params {})}
          ^BufferedWriter writer (io/writer (.getOutputStream process))
          notification-json      (json/write-value-as-string notification)]
      (timbre/debug "STDIO → (notification)" notification-json)
      (.write writer notification-json)
      (.newLine writer)
      (.flush writer)))

  (close [_]
    (.destroy process)))

(defn create-stdio-transport
  "Create a stdio transport (starts the process)"
  [config]
  (->StdioTransport (start-process config)))
