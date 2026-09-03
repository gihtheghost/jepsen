(ns meuteste.core
  (:import [client Client])
  (:require [clojure.tools.logging :refer :all]
            [clojure.string :as str]
            [jepsen [cli :as cli]
             [client :as j-client]
             [control :as c]
             [db :as db]
             [tests :as tests]
             [generator :as gen]
             [checker :as checker]]
            [jepsen.control.util :as cu]
            [jepsen.os.debian :as debian]
            [jepsen.control.net :as net]
            [knossos.model :as model]
            [jepsen.checker :as checker])
  (:import (knossos.model Model)))

(def dir     "/opt/treplicadb")
;(def pidfile (str dir "/treplicadb.pid"))
(def logfile (str dir "/server.log"))

(defn r   [_ _] {:type :invoke, :f :read, :value nil})
(defn w   [_ _] {:type :invoke, :f :write, :value (rand-int 5)})

(defn parse-long-nil
  "Parses a string to a Long. Passes through `nil`."
  [s]
  (when s (parse-long s)))

(defn translate-server-read
  "Separa a resposta de read em status e valor e retorna o mapa correto"
  [line]
  (let [[status valor] (str/split line #":\s*" 2)]
    (case status
      "OK" {:type :ok, :value (if (= valor "null") nil (parse-long-nil valor))}
      "ERRO" {:type :fail, :error valor}
      (throw (ex-info "Unknown server response" {:line line})))))

(defn translate-server-write
  "Separa a resposta de write em status e valor e retorna o mapa correto"
  [line]
  (let [[status valor] (str/split line #":\s*" 2)]
    (case status
      "OK" {:type :ok}
      "ERRO" {:type :fail, :error valor}
      (throw (ex-info "Unknown server response" {:line line})))))


(defn db
  "Treplica DB setup and teardown."
  []
  (reify db/DB
    (setup! [_ test node]
      (info node "iniciando database-with-treplica")
      (let [state-dir (str "/opt/treplicadb/state/" node) ; define um state/n1 para cada node
            pidfile (str "/opt/treplicadb/" node ".pid")] ; define um arquivo para colocar o pid do processo de cada node

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
           :match-process-name? true
           :exec "/usr/bin/java"}
          "/usr/bin/java" "-cp" "build/classes:lib/*" "src.database.Server"
          "5" "200" state-dir "6666") 
         ))
      
      (Thread/sleep 10000))


    (teardown! [_ test node]
      (info node "terminando database-with-treplica")
      (let [pidfile (str "/opt/treplicadb/" node ".pid")]
        (cu/stop-daemon! pidfile)
        (c/su (c/exec :rm :-rf "/database-with-treplica")
              (c/exec :pkill :-f "src.database.Server")) ;só pra ter certeza que matou o servidor mesmo
        ))

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
    (case (:f op)
      :read (let [response (.get conn "foo")]
              (merge op (translate-server-read response)))
      :write (let [response (.put conn "foo" (str (:value op)))]
               (merge op (translate-server-write response)))))

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
          :checker (checker/compose
                    {:perf (checker/perf)
                     :linear (checker/linearizable {:model (model/register)
                                                    :algorithm :linear})})
          :generator (->> (gen/mix [r w])
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
