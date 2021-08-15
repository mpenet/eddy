(ns s-exp.jetty.http.interceptor.ring1
  "adapted/taken from ring.util.servlet"
  (:require
   [s-exp.jetty.http.server.request :as request]
   [s-exp.jetty.http.server.response :as response]
   [exoscale.interceptor :as ix]
   [s-exp.jetty.http.interceptor.ring1 :as ring1]
   [qbits.auspex :as ax])
  (:import (org.eclipse.jetty.server Request Response)
           (jakarta.servlet AsyncContext)))

;;; interceptors

(def read-headers
  {:name ::read-request
   :enter
   (fn [{:as ctx :s-exp.jetty.http.server/keys [^Request request]}]
     (assoc ctx
            :ring1/request
            {:server-port (request/server-port request)
             :server-name (request/server-name request)
             :remote-addr (request/remote-addr request)
             :uri (request/uri request)
             :query-string (request/query-string request)
             :scheme (request/scheme request)
             :request-method (request/request-method request)
             :protocol (request/protocol request)
             :headers (request/headers request)
             :content-type (request/content-type request)
             :content-length (request/content-length request)
             :character-encoding (request/character-encoding request)
             :ssl-client-cert (request/ssl-client-cert request)}))})

(def read-body
  {:name ::read-body-sync
   :enter (fn [{:as ctx :s-exp.jetty.http.server/keys [^Request request]}]
            (assoc-in ctx [:ring1/request :body] (.getInputStream request)))})

(def write-headers
  {:name ::write-response
   :enter (fn [{:as ctx
                :s-exp.jetty.http.server/keys [^Response response]}]
            (let [{:keys [status headers]} (:ring1/response ctx)]

              (when (nil? response)
                (throw (NullPointerException. "HttpServletResponse is nil")))

              (when (nil? response)
                (throw (NullPointerException. "Response map is nil")))

              (when status
                (response/set-status! response status))

              (when headers
                (response/set-headers! response headers))

              ctx))})

(def write-body
  {:name ::write-body
   :enter (fn [{:as ctx
                :s-exp.jetty.http.server/keys [^Response response]}]
            (response/set-body! response (-> ctx :ring1/response :body))
            ctx)})

(def write-body-async
  {:name ::write-body-async
   :enter (fn [{:as ctx
                :s-exp.jetty.http.server/keys [^Response response]}]
            (ax/then (response/set-body-async! response (-> ctx :ring1/response :body))
                     (fn [_] ctx)))})

(def handle-request
  {:name ::handle-request
   :enter (-> (fn [{:as ctx :ring1/keys [handler request]}]
                (handler request))
              (ix/out [:ring1/response]))})

(def handle-request-async
  {:name ::handle-request
   :enter (-> (fn [{:as _ctx :ring1/keys [handler request]}]
                (handler request))
              (ix/out [:ring1/response]))})


(def init-request
  {:leave (fn [{:as ctx
                :s-exp.jetty.http.server/keys [^Request request]}]
            (request/complete! request))
   :error (fn [{:s-exp.jetty.http.server/keys [^Request request]}
               e]
            (print e)
            (request/complete! request)
            (throw e))})

(def init-request-async
  {:enter (fn [{:as ctx
                :s-exp.jetty.http.server/keys [^Request request
                                               async-timeout]}]
            (let [async-ctx (request/start-async request)]
              (when async-timeout
                (.setTimeout ^AsyncContext async-ctx async-timeout)))
            ctx)})

(def chain
  [init-request
   read-headers
   read-body
   handle-request
   write-headers
   write-body])

(def async-chain
  [init-request-async
   read-headers
   read-body
   handle-request-async
   write-headers
   write-body-async])
