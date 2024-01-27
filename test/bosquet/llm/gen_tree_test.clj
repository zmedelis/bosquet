(ns bosquet.llm.gen-tree-test
  (:require
   [bosquet.llm.gen-tree :refer [collapse-resolved-tree depend-tree
                                 expand-dependencies]]
   [clojure.test :refer [deftest is]]))

(deftest template->tree

  (is (= {:bosquet.depseq/tasks-0 "First, I am doing {{A}}"}
         (depend-tree :tasks "First, I am doing {{A}}")))

  (is (= {:bosquet.depseq/tasks-0 "First, I am doing {{A}}"
          :bosquet.depseq/tasks-1 "{{bosquet..depseq/tasks-0}} followed by {{B}}"
          :bosquet.depseq/tasks-2 "{{bosquet..depseq/tasks-1}} task."}
         (depend-tree :tasks "First, I am doing {{A}} followed by {{B}} task.")))

  (is (= {:bosquet.depseq/tasks__today-0 "First, I am doing {{A}}"}
         (depend-tree :tasks/today "First, I am doing {{A}}"))))

(deftest dep-tree-expantion
  (is (= {:bosquet.depseq/tasks-0 "First, I am doing {{A}}"
          :bosquet.depseq/tasks-1 "{{bosquet..depseq/tasks-0}} followed by {{B}}"
          :bosquet.depseq/tasks-2 "{{bosquet..depseq/tasks-1}} task."
          :bosquet.depseq/date-0  "TODAY"
          :bosquet.depseq/log-0   "{{date}}"
          :bosquet.depseq/log-1   "{{bosquet..depseq/log-0}} {{tasks}}"
          :A                    {:llm :agi}
          :B                    {:agent :007}}
         (expand-dependencies
          {:tasks "First, I am doing {{A}} followed by {{B}} task."
           :A     {:llm :agi}
           :B     {:agent :007}
           :date  "TODAY"
           :log   "{{date}} {{tasks}}"}))))

(deftest dep-tree-collapsing
  (is (= {:today/tasks "A B C"
          :log   "1 2"
          :A     {:llm :agi}}
         (collapse-resolved-tree
          {:bosquet.depseq/today__tasks-0 "A"
           :bosquet.depseq/today__tasks-1 "A B"
           :bosquet.depseq/today__tasks-2 "A B C"
           :A                    {:llm :agi}
           :bosquet.depseq/log-0   "1"
           :bosquet.depseq/log-1   "1 2"}))))
