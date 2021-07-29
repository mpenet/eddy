(ns s-exp.jetty.http.server
  (:require [exoscale.interceptor :as interceptor]
            [exoscale.interceptor.auspex]
            [s-exp.jetty.http.interceptor.ring1 :as ring1]
            [s-exp.jetty.http.server.protocols :as p]
            [qbits.auspex :as ax])

  (:import
   (jakarta.servlet AsyncContext)
   (jakarta.servlet.http HttpServletResponse
                         HttpServletRequest)
   (org.eclipse.jetty.server Request
                             Server
                             Request
                             HttpConnectionFactory
                             SslConnectionFactory
                             SecureRequestCustomizer
                             ServerConnector
                             HttpConfiguration
                             HttpConnectionFactory
                             ConnectionFactory)
   (org.eclipse.jetty.alpn.server ALPNServerConnectionFactory)
   (org.eclipse.jetty.http2.server HTTP2CServerConnectionFactory HTTP2ServerConnectionFactory)
   (org.eclipse.jetty.server.handler AbstractHandler)
   (org.eclipse.jetty.util.ssl SslContextFactory$Server
                               KeyStoreScanner)
   (org.eclipse.jetty.util BlockingArrayQueue)
   (org.eclipse.jetty.util.thread QueuedThreadPool
                                  ThreadPool)))

(set! *warn-on-reflection* true)

(def default-opts
  (merge
   #:s-exp.jetty.http.server{:join? true}
   #:s-exp.jetty.http.server.interceptor{:chain ring1/chain
                                         :ctx {}}
   #:s-exp.jetty.http.server.threadpool{:daemon? false
                                        :max-threads 50
                                        :min-threads 8
                                        :max-queued-requests Integer/MAX_VALUE
                                        :idle-timeout 60000}
   #:s-exp.jetty.http.server.http-config{:secure-scheme "https"
                                         :output-buffer-size 32768
                                         :request-header-size 8192
                                         :response-header-size 8192
                                         :send-server-version? false
                                         :send-date-header? false
                                         :header-cache-size 512}
   #:s-exp.jetty.http.server.http-connector{:port 8080
                                            :max-idle-time 200000}
   #:s-exp.jetty.http.server.ssl-connector{:max-idle-time 200000}
   #:s-exp.jetty.ssl-context-factory{}))

(defn initial-context
  [ctx base-request servlet-request servlet-response]
  (into ctx
        #:s-exp.jetty.http.server{:base-request base-request
                                  :request servlet-request
                                  :response servlet-response}))

(defn- send-error!
  [^HttpServletResponse servlet-response ^Throwable e]
  (.sendError servlet-response 500 (ex-message e)))

(defn create-handler
  [f]
  (proxy [AbstractHandler] []
    (handle [target
             ^Request request
             ^HttpServletRequest servlet-request
             ^HttpServletResponse servlet-response]
      (f target
         request
         servlet-request
         servlet-response))))

(defn handler
  [{:as _opts
    :s-exp.jetty.http.server.interceptor/keys [chain ctx]}]
  (create-handler
   (fn [_target
        ^Request request
        ^HttpServletRequest servlet-request
        ^HttpServletResponse servlet-response]
     (try
       (-> (initial-context ctx
                            request
                            servlet-request
                            servlet-response)
           (interceptor/execute chain))
       (catch Throwable e
         (prn e)
         (send-error! servlet-response e))
       (finally
         (.setHandled request true))))))



(defn- create-threadpool
  ^ThreadPool
  [{:s-exp.jetty.http.server.threadpool/keys [max-threads min-threads
                                              idle-timeout daemon?
                                              max-queued-requests]}]

  (let [queue-max-capacity (max max-queued-requests 8)
        queue-capacity (min (max min-threads 8)
                            queue-max-capacity)
        blocking-queue (BlockingArrayQueue. queue-capacity
                                            queue-capacity
                                            queue-max-capacity)
        pool (QueuedThreadPool. max-threads
                                min-threads
                                idle-timeout
                                blocking-queue)]
    (when daemon?
      (.setDaemon pool true))
    pool))

(defn- http-config
  ^HttpConfiguration
  [{:s-exp.jetty.http.server.http-config/keys
    [output-buffer-size request-header-size response-header-size
     send-server-version? send-date-header?
     header-cache-size]}]
  (doto (HttpConfiguration.)
    (.setOutputBufferSize output-buffer-size)
    (.setRequestHeaderSize request-header-size)
    (.setResponseHeaderSize response-header-size)
    (.setSendServerVersion send-server-version?)
    (.setSendDateHeader send-date-header?)
    (.setHeaderCacheSize header-cache-size)))

(defn- ssl-context-factory
  ^SslContextFactory$Server
  [{:s-exp.jetty.ssl-context-factory/keys
    [keystore keystore-type key-password client-auth  truststore trust-password
     exclude-ciphers replace-exclude-ciphers? exclude-protocols
     replace-exclude-protocols? ssl-context]}]
  (let [context-server (SslContextFactory$Server.)]
    (if (string? keystore)
      (.setKeyStorePath context-server keystore)
      (.setKeyStore context-server ^java.security.KeyStore keystore))
    (when (string? keystore-type)
      (.setKeyStoreType context-server keystore-type))
    (.setKeyStorePassword context-server key-password)
    (cond
      (string? truststore)
      (.setTrustStorePath context-server truststore)
      (instance? java.security.KeyStore truststore)
      (.setTrustStore context-server ^java.security.KeyStore truststore))
    (when trust-password
      (.setTrustStorePassword context-server trust-password))
    (when ssl-context
      (.setSslContext context-server ssl-context))
    (case client-auth
      :need (.setNeedClientAuth context-server true)
      :want (.setWantClientAuth context-server true)
      nil)
    (when-let [exclude-ciphers exclude-ciphers]
      (let [ciphers (into-array String exclude-ciphers)]
        (if replace-exclude-ciphers?
          (.setExcludeCipherSuites context-server ciphers)
          (.addExcludeCipherSuites context-server ciphers))))
    (when-let [exclude-protocols exclude-protocols]
      (let [protocols (into-array String exclude-protocols)]
        (if replace-exclude-protocols?
          (.setExcludeProtocols context-server protocols)
          (.addExcludeProtocols context-server protocols))))
    context-server))

(defn- server-connector
  ^ServerConnector
  [^Server server factories]
  (ServerConnector. server
                    #^"[Lorg.eclipse.jetty.server.ConnectionFactory;"
                    (into-array ConnectionFactory factories)))

(defn- ^ServerConnector ssl-connector
  [^Server server
   {:as opts
    :s-exp.jetty.http.server.ssl-connector/keys [host port keystore-scan-interval
                                                 max-idle-time http2?]}]
  (let [http-cfg (http-config opts)
        http-factory (HttpConnectionFactory.
                      (doto http-cfg
                        (.setSecureScheme "https")
                        (.setSecurePort port)
                        (.addCustomizer (SecureRequestCustomizer.))))
        ssl-context  (ssl-context-factory opts)
        connection-factories (cond-> [(SslConnectionFactory. ssl-context "http/1.1")]
                               http2?
                               (conj (ALPNServerConnectionFactory. "h2,http/1.1")
                                     (HTTP2ServerConnectionFactory. http-cfg))
                               :then (conj http-factory))]
    (when keystore-scan-interval
      (.addBean server
                (doto (KeyStoreScanner. ssl-context)
                  (.setScanInterval keystore-scan-interval))))
    (doto (server-connector server connection-factories)
      (.setPort port)
      (.setHost host)
      (.setIdleTimeout max-idle-time))))

(defn- http-connector
  ^ServerConnector
  [server {:s-exp.jetty.http.server.http-connector/keys [host port max-idle-time http2?]
           :as opts}]
  (let [http-cfg (http-config opts)
        connection-factories (cond-> [(HttpConnectionFactory. http-cfg)]
                               http2?
                               (conj (HTTP2CServerConnectionFactory. http-cfg)))]
    (doto (server-connector server connection-factories)
      (.setPort port)
      (.setHost host)
      (.setIdleTimeout max-idle-time))))

(defn- create-server
  ^Server
  [{:as opts}]
  (let [server (Server. (create-threadpool opts))]
    (when (:s-exp.jetty.http.server.http-connector/port opts)
      (.addConnector server (http-connector server opts)))
    (when (:s-exp.jetty.http.server.ssl-connector/port opts)
      (.addConnector server (ssl-connector server opts)))
    server))

(defn run
  ^Server
  [opts]
  (let [{:as opts
         :s-exp.jetty.http.server/keys [configurator join? handler]
         :or {handler handler}} (merge default-opts opts)
        server (create-server opts)]
    (.setHandler server (handler opts))
    (when configurator
      (configurator server))
    (.start server)
    (when join?
      (.join server))
    server))

(def response {:status 200 :body ""})

;; (def s (run {:s-exp.jetty.http.server/join? false
;;              :s-exp.jetty.http.server.threadpool/max-threads 10
;;              :s-exp.jetty.http.server.threadpool/min-threads 10
;;              :s-exp.jetty.http.server.interceptor/chain ring1/async-chain
;;              :s-exp.jetty.http.server.interceptor/ctx
;;              {:ring1/handler (fn [request] (ax/success-future response))}}))

(def s (run {:s-exp.jetty.http.server/join? false
             :s-exp.jetty.http.server.threadpool/max-threads 10
             :s-exp.jetty.http.server.threadpool/min-threads 10
             :s-exp.jetty.http.server.interceptor/chain ring1/chain
             :s-exp.jetty.http.server.interceptor/ctx
             {:ring1/handler (fn [request] response)}}))

;; (def s (run {:s-exp.jetty.http.server/join? false
;;              :s-exp.jetty.http.server.threadpool/max-threads 50
;;              :s-exp.jetty.http.server.threadpool/min-threads 50
;;              :s-exp.jetty.http.server/handler
;;              (fn [opts]
;;                (create-handler (fn [_ ^Request request ^HttpServletRequest s-request ^HttpServletResponse  s-response]
;;                                  (.setStatus s-response 200)
;;                                  (.setHandled request true))))}))

;; (def s (run {:s-exp.jetty.http.server/join? false
;;              :s-exp.jetty.http.server.threadpool/max-threads 50
;;              :s-exp.jetty.http.server.threadpool/min-threads 50
;;              :s-exp.jetty.http.server/handler
;;              (fn [opts]
;;                (create-handler (fn [_ ^Request request ^HttpServletRequest
;;                                     s-request ^HttpServletResponse  s-response]

;;                                  (p/set-status! s-response 200)
;;                                  (.setHandled request true))))}))


(.stop s)
