(ns s-exp.jetty.http.server.protocols)

(defprotocol Request
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
  (read-body [request]))

(defprotocol AsyncContext
  (async-supported? [request])
  (async-started? [request])
  (async-context [request])
  (start-async [request]))

(defprotocol Response
  (set-status! [response status])
  (set-headers! [response headers]))

(defprotocol BodyWriter
  (set-body! [response body]))

(defprotocol BodyWriterAsync
  (set-body-async! [response body]))

(defprotocol WriteListener
  (set-write-listener! [response listener]))

(defprotocol ReadListener
  (set-read-listener! [request listener]))

(defprotocol WriteBody
  (write-body! [body response]))

(defprotocol WriteBodyAsync
  (write-body-async! [body response]))
