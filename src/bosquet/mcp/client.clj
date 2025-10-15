(ns bosquet.mcp.client
  "MCP client that spawns and communicates with MCP server processes"
  (:require [bosquet.mcp.transport :refer [send-request send-notification close]]
            [bosquet.mcp.stdio-transport :as stdio-transport]))

(defn create-transport
  "Create appropriate transport based on config.
   Automatically starts processes for stdio transport."
  [{:keys [type] :as config}]
  (case (or type :stdio)
    :stdio (stdio-transport/create-stdio-transport config)
    :http  (throw (ex-info "http: Not yet supported" {:type type :config config}))
    (throw (ex-info "Unknown transport type" {:type type :config config}))))

(defn initialize
  "Initialize MCP connection"
  [transport]
  (let [response (send-request transport "initialize"
                               {:protocolVersion "2024-11-05"
                                :capabilities {:tools {}}
                                :clientInfo {:name "bosquet-mcp" :version "1.0.0"}})]
    (send-notification transport "notifications/initialized" {})
    response))

(defn list-tools
  "List available tools"
  [transport]
  (get-in (send-request transport "tools/list" {}) [:result :tools]))

(defn call-tool
  "Call a tool"
  [transport tool-name arguments]
  (get-in (send-request transport "tools/call"
                        {:name tool-name :arguments arguments})
          [:result :content]))

(defn shutdown
  "Shutdown MCP connection"
  [transport]
  (close transport))
