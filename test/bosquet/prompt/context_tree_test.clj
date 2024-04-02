(ns bosquet.prompt.context-tree-test
  (:require
   [bosquet.prompt.context-tree :refer
             [collapse-resolved-tree depend-tree expand-dependencies
              partition-template]]
   [clojure.test :refer [deftest is]]))

(deftest tempalte-partitioning
  (is (= ["const"] (partition-template "const")))
  (is (= ["Hi {{name|filter}}" "!"] (partition-template "Hi {{name|filter}}!")))
  (is (= ["E: {{x}}" " + {{y}}" " = {{z}}" "!"]
         (partition-template "E: {{x}} + {{y}} = {{z}}!"))))

(deftest template->tree

  (is (= {:bosquet.context-tree/tasks-0 "First, I am doing {{A}}"}
         (depend-tree :tasks "First, I am doing {{A}}")))

  (is (= {:bosquet.context-tree/tasks-0 "First, I am doing {{A}}"
          :bosquet.context-tree/tasks-1 "{{bosquet..context-tree/tasks-0}}\nfollowed by {{B}}"
          :bosquet.context-tree/tasks-2 "{{bosquet..context-tree/tasks-1}} task."}
         (depend-tree :tasks "First, I am doing {{A}}\nfollowed by {{B}} task.")))

  (is (= {:bosquet.context-tree/tasks__today-0 "First, I am doing {{A}}"}
         (depend-tree :tasks/today "First, I am doing {{A}}"))))

(deftest dep-tree-expantion
  (is (= {:bosquet.context-tree/tasks-0 "First, I am doing {{A}}"
          :bosquet.context-tree/tasks-1 "{{bosquet..context-tree/tasks-0}} followed by {{B}}"
          :bosquet.context-tree/tasks-2 "{{bosquet..context-tree/tasks-1}} task."
          :bosquet.context-tree/date-0  "TODAY"
          :bosquet.context-tree/log-0   "{{date}}"
          :bosquet.context-tree/log-1   "{{bosquet..context-tree/log-0}} {{tasks}}"
          :A                    {:llm :agi}
          :B                    {:agent :blue}}
         (expand-dependencies
          {:tasks "First, I am doing {{A}} followed by {{B}} task."
           :A     {:llm :agi}
           :B     {:agent :blue}
           :date  "TODAY"
           :log   "{{date}} {{tasks}}"}))))

(deftest dep-tree-collapsing
  (is (= {:today/tasks "A B C"
          :log   "1 2"
          :A     {:llm :agi}}
         (collapse-resolved-tree
          {:bosquet.context-tree/today__tasks-0 "A"
           :bosquet.context-tree/today__tasks-1 "A B"
           :bosquet.context-tree/today__tasks-2 "A B C"
           :A                    {:llm :agi}
           :bosquet.context-tree/log-0   "1"
           :bosquet.context-tree/log-1   "1 2"}))))
