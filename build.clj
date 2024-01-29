(ns build
  (:require [clojure.tools.build.api :as b]
            [clojure.edn :as edn]
            [deps-deploy.deps-deploy :as dd]))

(def project (-> (edn/read-string (slurp "deps.edn"))
                 :aliases :neil :project))
(def lib (or (:name project) 'my/lib1))

;; use neil project set version 1.2.0 to update the version in deps.edn

(def version (or (:version project)
                 "1.2.0"))
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def uber-file (format "target/%s-%s-standalone.jar" (name lib) version))
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"}))

(defn- pom-template [version]
  [[:description "Tooling to build LLM applications: prompt templating and composition, agents, LLM memory, and other instruments for builders of AI applications."]
   [:url "https://github.com/zmedelis/bosquet"]
   [:licenses
    [:license
     [:name "Eclipse Public License"]
     [:url "http://www.eclipse.org/legal/epl-v10.html"]]]
   [:developers
    [:developer
     [:name "Žygimantas Medelis"]]]
   [:scm
    [:url "https://github.com/zmedelis/bosquet"]
    [:connection "scm:git:https://github.com/zmedelis/bosquet.git"]
    [:developerConnection "scm:git:ssh:git@github.com:zmedelis/bosquet.git"]
    [:tag (str "v" version)]]])

(defn jar [_]
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis basis
                :pom-data  (pom-template version)
                :src-dirs ["src"]})
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn install [_]
  (jar {})
  (b/install {:basis basis
              :lib lib
              :version version
              :jar-file jar-file
              :class-dir class-dir}))

(defn uber [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis basis}))

(defn deploy [opts]
  (jar opts)
  (dd/deploy {:installer :remote
              :artifact  (b/resolve-path jar-file)
              :pom-file  (b/pom-path {:lib lib
                                      :class-dir class-dir})}))
