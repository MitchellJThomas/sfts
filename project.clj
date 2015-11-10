(defproject sfts "0.1.0-SNAPSHOT"
  :description "Suck down Searching For The Sound shows"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [http-kit "2.1.19"]
                 [hickory "0.5.4"]
                 [clj-time "0.11.0"]]
  :main ^:skip-aot sfts.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}
  :plugins [[cider/cider-nrepl "0.10.0-SNAPSHOT"]]
  )
