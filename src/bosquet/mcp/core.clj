(ns bosquet.mcp.core
  (:require [clojure.string :as str]
            [taoensso.timbre :as timbre]
            [bosquet.mcp.client :as client]))

(def ^:dynamic *transports* (atom {}))
(def ^:dynamic *tool-vars* (atom []))

(defn create-tool-fn
  "Create a Bosquet-compatible tool function"
  [transport tool]
  (let [tool-name (symbol (:name tool))
        input-schema (:inputSchema tool)
        properties (:properties input-schema {})
        required (map keyword (:required input-schema []))
        param-names (vec (concat required
                                 (remove (set required) (keys properties))))
        tool-fn (fn [& args]
                  (timbre/info "Calling with args:" args)
                    ;; Convert keywords back to strings for MCP
                  (let [arguments (zipmap (map name param-names) args)]
                    (timbre/info "Mapped to:" arguments)
                    (let [result (client/call-tool transport (:name tool) arguments)]
                      (if (sequential? result)
                        (clojure.string/join " " (map #(get % "text" "") result))
                        (str result)))))]

    (intern 'bosquet.mcp.tools
            tool-name
            (with-meta tool-fn
              {:doc (:description tool)
               :desc (:description tool)
               :arglists (list (vec (map (fn [pname]
                                           (let [pdef (get properties pname)]
                                             (with-meta (symbol (name pname))
                                               {:type (get pdef :type "string")
                                                :desc (get pdef :description "")})))
                                         param-names)))}))

    (ns-resolve 'bosquet.mcp.tools tool-name)))

(defn initialize-mcp-servers!
  "Initialize MCP servers - process spawning is handled automatically"
  [configs]
  (timbre/info "Initializing" (count configs) "MCP servers")
  (reset! *transports* {})
  (reset! *tool-vars* [])

  (doseq [[sym _] (ns-publics 'bosquet.mcp.tools)]
    (ns-unmap 'bosquet.mcp.tools sym))

  (doseq [config configs]
    (try
      (let [name (:name config)
            transport (client/create-transport config)]

        (timbre/info "Connecting to" name "via" (or (:type config) :stdio))

        (client/initialize transport)
        (swap! *transports* assoc name transport)

        (doseq [tool (client/list-tools transport)]
          (let [tool-var (create-tool-fn transport tool)]
            (swap! *tool-vars* conj tool-var)
            (timbre/info "  →" (:name tool))))

        (timbre/info "Ready:" name))

      (catch Exception e
        (timbre/error e "Failed:" (:name config)))))

  (timbre/info "All servers ready"))

(defn get-tool-vars
  "Helper to get all MCP tool vars for use in wkk/tools"
  []
  @*tool-vars*)

(defn shutdown-mcp!
  "Shutdown all MCP connections (kills processes for stdio)"
  []
  (doseq [[name transport] @*transports*]
    (client/shutdown transport)
    (timbre/info "Stopped" name))
  (reset! *transports* {})
  (reset! *tool-vars* []))

(comment
  (require '[bosquet.mcp.tools :as mcp-tools])
  (initialize-mcp-servers!
   [{:name "echo-server"
     :type :stdio
     :command "python3"
     :args ["resources/mcp-example/echo.py"]}])
  (mcp-tools/echo_multiple "hello world" "this is rohit")
  (mcp-tools/echo "Hello world")
  (shutdown-mcp!))
