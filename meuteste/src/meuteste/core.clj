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
(def logfile (str dir "/server.log"))

(defn db 
  "Treplica DB for a particular version." 
  []
  (reify db/DB
    (setup! [_ test node]
      (info node "iniciando database-with-treplica")
      (let [state-dir (str "/opt/treplicadb/state/" node)] ; define um state/n1 para cada node
        
        (c/su
         ; cria um state/n1 para cada node no volume compartilhado
         (c/exec :mkdir :-p state-dir)
         (c/exec :git :clone "https://github.com/gihtheghost/database-with-treplica.git")
         (c/cd "/database-with-treplica"
               (c/exec :ant :build))
         (cu/start-daemon! 
          {:logfile logfile
           :pidfile pidfile
           :chdir "/database-with-treplica"
           :background? true
           :make-pidfile? true
           :match-executable? true
           :exec "/usr/bin/java"}
          "/usr/bin/java" "-cp" "build/classes:lib/*" "src.database.Server"
          "5" "200" state-dir "6666")
         )))

    (teardown! [_ test node] 
               (info node "terminando database-with-treplica")
               (cu/stop-daemon! pidfile)
               (c/su (c/exec :rm :-rf "/database-with-treplica")))))


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
