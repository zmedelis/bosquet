(ns bosquet.mcp.transport)

(defprotocol MCPTransport
  "Protocol for MCP transport implementations"
  (send-request [this method params] "Send a request and return response")
  (send-notification [this method params] "Send a notification (no response expected)")
  (close [this] "Close the transport connection"))





