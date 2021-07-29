(ns s-exp.jetty.http.server.request
  (:require [s-exp.jetty.http.server.protocols :as p]
            [s-exp.jetty.utils :as u])
  (:import (jakarta.servlet.http HttpServletRequest)
           (org.eclipse.jetty.server Request)
           (jakarta.servlet AsyncContext)))

(defn- get-headers
  "Creates a name/value map of all the request headers."
  [^HttpServletRequest request]
  (reduce (fn [headers ^String name]
            (assoc headers
                   (.toLowerCase name)
                   (->> (.getHeaders request name)
                        (u/enum-reducible)
                        (transduce (interpose ",") u/string-builder))))
          {}
          (u/enum-reducible (.getHeaderNames request))))

(defn- get-client-cert
  "Returns the SSL client certificate of the request, if one exists."
  [^Request request]
  (first (.getAttribute request "javax.servlet.request.X509Certificate")))

(defn- get-content-length
  "Returns the content length, or nil if there is no content."
  [^Request request]
  (let [length (.getContentLength request)]
    (when (>= length 0) length)))

(extend-type Request
  p/Request
  (server-port [request] (.getServerPort request))
  (server-name [request] (.getServerName request))
  (remote-addr [request] (.getRemoteAddr request))
  (uri [request] (.getRequestURI request))
  (query-string [request] (.getQueryString request))
  (scheme [request] (.getScheme request))
  (request-method [request] (keyword (.toLowerCase (.getMethod request))))
  (protocol [request] (.getProtocol request))
  (headers [request] (get-headers request))
  (content-type [request] (.getContentType request))
  (content-length [request] (get-content-length request))
  (character-encoding [request] (.getCharacterEncoding request))
  (ssl-client-cert [request] (get-client-cert request))
  (complete! [request]
    (when (p/async-started? request)
      (.complete ^AsyncContext (p/async-context request))))

  p/BodyReader
  (read-body [request] (.getInputStream request))

  p/AsyncContext
  (async-supported? [request] (.isAsyncSupported request))
  (async-started? [request] (.isAsyncStarted request))
  (async-context [request] (.getAsyncContext request))
  (start-async [request] (.startAsync request)))
