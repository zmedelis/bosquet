(ns observability
  (:require
   [bosquet.llm.generator :as g]
   [bosquet.llm.http :as http]))

;; ## Observability through proxy
;;
;;
;; A local proxy logging all interactions between *Bosquet* and LLM service can be a very useful debugging tool.
;; For that purpose, Bosquet can be configured to work with with [Mitproxy](https://mitmproxy.org/).
;;
;; ## Install
;;
;; Follow the installation instructions in the *Mitproxy* [documentation](https://docs.mitmproxy.org/stable/overview-installation/).
;; Once installed you can start web console with `mitmweb`.
;;
;; *Mitproxy* webconsole http://127.0.0.1:8081/#/flows
;;
;; As Mitproxy starts it will create a `~/.mitproxt` dir containing SSL certificates.
;; The certificate needs to be added to JVM keystore.
;;
;; The following command will add it to *Bosquet* keystore:
;;
;; ```bash
;; bb mitproxy:keystore
;; ```
;;
;; ## REPL
;;
;; When in REPL, this call will set JVM parameters forcing HTTP libs to use a configured proxy.

^{:nextjournal.clerk/visibility {:result :hide}}
(http/use-local-proxy)

;; This function sets the following JVM properties
;; ```clojure
;; (System/setProperty "javax.net.ssl.trustStore" (str (System/getProperty "user.home") "/.bosquet/keystore"))
;; (System/setProperty "javax.net.ssl.trustStorePassword" password)
;; (System/setProperty "https.proxyHost" host)
;; (System/setProperty "https.proxyPort" (str port))
;; ```
;; After this, the `generate` call will go through Mitproxy

(g/generate
 [[:system ["As a brilliant astronomer, list distances between planets and the Sun"
            "in the Solar System. Provide the answer in JSON map where the key is the"
            "planet name and the value is the string distance in millions of kilometers."
            "{{analysis}}"]]
  [:user ["Generate only JSON omit any other prose and explanations."]]
  [:assistant (g/llm :openai
                     :llm/var-name :distances
                     :llm/output-format :json
                     :llm/model-params {:max-tokens 300 :model :gpt-4})]
  [:user ["Based on the JSON distances data"
          "provide me with​ a) average distance b) max distance c) min distance"]]
  [:assistant (g/llm :mistral
                     :llm/var-name :analysis
                     :llm/model-params {:model :mistral-small})]])

;; The *Mitproxy* console should show logged calls, where request, response, and latency data can be examined.
;;
;; ![Mitproxy](notebook/assets/mitproxy.png)
;;
;; ### CLI
;;
;; When using Bosquet via command line, proxy can be activated either with defaults:
;;
;; ```bash
;; clojure -M -m bosquet.cli "2+2=" --proxy
;; ```
;; or with custom host and port
;; ```bash
;; clojure -M -m bosquet.cli "2+2=" --proxy-host localhost --proxy-port 8080 --keystore-password changeit
;; ```
;; ---
;; *With many thanks to [Fuck You, Show Me The Prompt](https://hamel.dev/blog/posts/prompt/)*
