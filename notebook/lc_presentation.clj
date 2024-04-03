(ns lc-presentation)

;; #### Prompt template
;; Initial version of the prompt template included a custom Selmer tag to trigger definitions:
;; ```
;; Question: \{{Q}}
;; Answer: {% gen var-name=answer model=gpt-4 temperature=0.2 %}
;; ```
;; This was abandoned in favor of less Selmer customization and ability to define LLM calls externally
;; ```edn
;; {:q1   ["Q: When I was {{age}} my sister was half my age."
;;         "Now I’m 70 how old is my sister? A: {{a}}"]
;;  :a    {:llm/service :ollama :llm/model-params {:model :llama2})}
;; ```
;;
;; More in /notebook/user_guide -> Define tempaltes
;;
;; #### Environment setup
;; Why a simple call like this works
;; ```
;; (gen "xxx")
;; ```
;; Bosquet adds a default llm config turning this into a
;; ```edn
;; {:prompt "xxx {{gen}}"
;;  :gen :DEFAULT-LLM}
;; ```
;; The initial solution was based on Integrant but that was too cumbersome and hard to extend.
;; The defaults are defined like this: `env.edn`
;; OR (and this I learned from the early lib users) people want to pass in custom definitions:
