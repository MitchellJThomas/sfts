(defproject sfts "0.1.0-SNAPSHOT"
  :description "Suck down Searching For The Sound shows"
  :license {:name "Apache Licence v2"
            :url "http://www.apache.org/licenses/"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [http-kit "2.1.19"]
                 [hickory "0.5.4"]
                 [clj-time "0.11.0"]
                 [green-tags "0.3.0-alpha"]]
  :main ^:skip-aot sfts.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}
  :plugins [[cider/cider-nrepl "0.10.0-SNAPSHOT"]]
  )
