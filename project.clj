(defproject pipe "0.1.0-SNAPSHOT"
  :description "core.async http proxy"
  :url "http://github.com/razum2um/pipe"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.taoensso/timbre "3.3.1"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [http-kit "2.1.19"]]
  :main ^:skip-aot pipe.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
