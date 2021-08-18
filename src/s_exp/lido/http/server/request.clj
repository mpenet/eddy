(ns s-exp.lido.http.server.request
  (:require [s-exp.lido.utils :as u])
  (:import (jakarta.servlet.http HttpServletRequest)
           (org.eclipse.jetty.server Request)
           (jakarta.servlet AsyncContext)))

(defprotocol RequestReader
  (server-port [request])
  (server-name [request])
  (remote-addr [request])
  (uri [request])
  (query-string [request])
  (scheme [request])
  (request-method [request])
  (protocol [request])
  (headers [request])
  (content-type [request])
  (content-length [request])
  (character-encoding [request])
  (ssl-client-cert [request])

  (complete! [request])
  (set-handled! [request handled?]))

(defprotocol BodyReader
  (-read-body [request]))

(defprotocol ReadListener
  (-set-read-listener! [request listener]))

(defprotocol AsyncCtx
  (async-supported? [request])
  (async-started? [request])
  (async-context [request])
  (start-async [request]))

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
  RequestReader
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
    (when (async-started? request)
      (.complete ^AsyncContext (async-context request))))
  (set-handled! [request handled?]
    (.setHandled request handled?))

  BodyReader
  (read-body [request] (.getInputStream request))

  ReadListener
  (set-read-listener! [request listener]
    (-> request (.getInputStream) (.setReadListener listener)))

  AsyncCtx
  (async-supported? [request] (.isAsyncSupported request))
  (async-started? [request] (.isAsyncStarted request))
  (async-context [request] (.getAsyncContext request))
  (start-async [request] (.startAsync request)))
