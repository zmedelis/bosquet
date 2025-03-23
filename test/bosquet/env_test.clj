(ns bosquet.env-test
  (:require
   [bosquet.env :as env]
   [clojure.string :as str]
   [clojure.test :as t]))

(t/deftest config-location
  (with-redefs [env/exists? (fn [_d] true)]
    (t/is (str/starts-with?
           (.getPath (env/bosquet-cfg-file "config.edn"))
           "./")))
  (with-redefs [env/exists? (fn [_d] false)]
    (t/is (= "./config.edn" (.getPath (env/bosquet-cfg-file "config.edn"))))))
