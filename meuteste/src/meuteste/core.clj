(ns meuteste.core
  (:import [client Client])
  (:require [clojure.tools.logging :refer :all]
            [clojure.string :as str]
            [jepsen [cli :as cli]
             [client :as j-client]
             [control :as c]
             [db :as db]
             [tests :as tests]
             [generator :as gen]] 
            [jepsen.control.util :as cu]
            [jepsen.os.debian :as debian]
            [jepsen.control :as c]
            [jepsen.generator :as gen]))

(def dir     "/opt/treplicadb")
(def pidfile (str dir "/treplicadb.pid"))
(def logfile (str dir "/server.log"))

(defn r   [_ _] {:type :invoke, :f :read, :value nil})
(defn w   [_ _] {:type :invoke, :f :write, :value (rand-int 5)})

(defn db
  "Treplica DB setup and teardown."
  []
  (reify db/DB
    (setup! [_ test node]
      (info node "iniciando database-with-treplica")
      (let [state-dir (str "/opt/treplicadb/state/" node)] ; define um state/n1 para cada node

        (c/su
         ; apaga antigo logfile se ele existir
         (c/exec :rm :-rf logfile)
         ; apaga antigo e cria um state/n1 para cada node no volume compartilhado
         (c/exec :rm :-rf state-dir)
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
          "5" "200" state-dir "6666"))))

    (teardown! [_ test node]
      (info node "terminando database-with-treplica")
      (cu/stop-daemon! pidfile)
      (c/su (c/exec :rm :-rf "/database-with-treplica")))
    
    ; guarda logfiles no control node antes do teardown
    db/LogFiles
    (log-files [_ test node]
      [logfile])))


(defrecord TreplicaClient [conn]
  j-client/Client
  (open! [this test node]
    (let [java-client (Client. node 6666)]
      (.connect java-client)
      (assoc this :conn java-client))) ; assoc a instancia do cliente com o conn

  (setup! [this test])

  (invoke! [this test op]
           (let [c (:conn this)]
           (case (:f op)
             :read (assoc op :type :ok, :value (.get c "foo")))))

  (teardown! [this test])

  (close! [this test]
    (.quit conn)))

(defn etcd-test
  "Given an options map from the command line runner (e.g. :nodes, :ssh,
  :concurrency ...), constructs a test map."
  [opts]
  (merge tests/noop-test
         opts
         {:name "etcd"
          :os   debian/os
          :db   (db)
          :pure-generators true
          :client (TreplicaClient. nil)
          :generator (->> r
                          (gen/stagger 1)
                          (gen/nemesis nil)
                          (gen/time-limit 15))}))

(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
  browsing results."
  [& args]
  (cli/run! (merge (cli/single-test-cmd {:test-fn etcd-test})
                   (cli/serve-cmd))
            args))
