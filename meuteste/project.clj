(defproject meuteste "0.1.0-SNAPSHOT"
  :description "A Jepsen test for etcd"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :java-source-paths ["java_src"]
  :main meuteste.core
  :dependencies [[org.clojure/clojure "1.12.4"]
                 [jepsen "0.3.9"]]
  )
