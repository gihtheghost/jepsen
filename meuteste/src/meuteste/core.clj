(ns meuteste.core
  (:require [clojure.tools.logging :refer :all]
            [clojure.string :as str]
            [jepsen [cli :as cli]
             [control :as c]
             [db :as db]
             [tests :as tests]]
            [jepsen.control.util :as cu]
            [jepsen.os.debian :as debian]
            [jepsen.control :as c]))

(def dir     "/opt/treplicadb")
(def pidfile (str dir "/treplicadb.pid"))

(defn db 
  "Treplica DB for a particular version." ;isso aqui n vai dar certo pq n roda em backgroung, precisa de entrada do terminal
  []
  (reify db/DB
    (setup! [_ test node]
      (info node "iniciando treplica")
      (let [state-dir (str "opt/treplicadb/state/" node)] ; define um state/n1 para cada node
        
        (c/su
         ; cria um state/n1 para cada node no volume compartilhado
         (c/exec :mkdir :-p state-dir)
         (c/cd dir
               (c/exec :java :-cp "dist/treplica-0.5.0.jar:lib/slf4j-api-2.0.17.jar:lib/slf4j-nop-2.0.17.jar" "src.main.br.unicamp.treplica.examples.ReplicatedMap"
                       "5" "200" state-dir)))))

    (teardown! [_ test node]
      (info node "terminando treplica"))))

(defn etcd-test
  "Given an options map from the command line runner (e.g. :nodes, :ssh,
  :concurrency ...), constructs a test map."
  [opts]
  (merge tests/noop-test
         opts
         {:name "etcd"
          :os   debian/os
          :db   (db)
          :pure-generators true}))

(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
  browsing results."
  [& args]
  (cli/run! (merge (cli/single-test-cmd {:test-fn etcd-test})
                   (cli/serve-cmd))
            args))
