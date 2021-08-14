(ns s-exp.jetty.http.interceptor.ring1
  "adapted/taken from ring.util.servlet"
  (:require
   [s-exp.jetty.http.server.protocols :as p]
   [s-exp.jetty.http.server.request]
   [s-exp.jetty.http.server.response]
   [exoscale.interceptor :as ix]
   [s-exp.jetty.http.interceptor.ring1 :as ring1]
   [qbits.auspex :as ax])
  (:import (org.eclipse.jetty.server Request Response)
           (jakarta.servlet AsyncContext)))

;;; interceptors

(def read-headers
  {:name ::read-request
   :enter
   (fn [{:as ctx :s-exp.jetty.http.server/keys [^HttpServletRequest request]}]
     (assoc ctx
            :ring1/request
            {:server-port (p/server-port request)
             :server-name (p/server-name request)
             :remote-addr (p/remote-addr request)
             :uri (p/uri request)
             :query-string (p/query-string request)
             :scheme (p/scheme request)
             :request-method (p/request-method request)
             :protocol (p/protocol request)
             :headers (p/headers request)
             :content-type (p/content-type request)
             :content-length (p/content-length request)
             :character-encoding (p/character-encoding request)
             :ssl-client-cert (p/ssl-client-cert request)}))})

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
                (p/set-status! response status))

              (when headers
                (p/set-headers! response headers))

              ctx))})

(def write-body
  {:name ::write-body
   :enter (fn [{:as ctx
                :s-exp.jetty.http.server/keys [^Response response]}]
            (p/set-body! response (-> ctx :ring1/response :body))
            ctx)})

(def write-body-async
  {:name ::write-body-async
   :enter (fn [{:as ctx
                :s-exp.jetty.http.server/keys [^Response response]}]
            (ax/then (p/set-body-async! response (-> ctx :ring1/response :body))
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
            (p/complete! request))
   :error (fn [{:s-exp.jetty.http.server/keys [^Request request]}
               e]
            (print e)
            (p/complete! request)
            (throw e))})

(def init-request-async
  {:enter (fn [{:as ctx
                :s-exp.jetty.http.server/keys [^Request request
                                               async-timeout]}]
            (let [async-ctx (p/start-async request)]
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
