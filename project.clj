(defproject com.s-exp/jetty "0.1.0-SNAPSHOT"
  :url "https://github.com/mpenet/jetty"
  :dependencies [[org.clojure/clojure "1.10.2"]
                 [org.eclipse.jetty/jetty-server "11.0.6"]
                 [org.eclipse.jetty.http2/http2-server "11.0.6"]
                 [org.eclipse.jetty/jetty-alpn-server "11.0.6"]
                 [exoscale/interceptor "0.1.9"]
                 [cc.qbits/auspex "1.0.0-alpha7"]]

  :pedantic? :warn
  :global-vars {*warn-on-reflection* true}

  :profiles {:dev {:dependencies [[clj-http "3.12.3"]]}})
