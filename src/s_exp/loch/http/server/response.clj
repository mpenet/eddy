(ns s-exp.loch.http.server.response
  (:require [clojure.java.io :as io]
            [qbits.auspex :as ax])
  (:import (java.util.concurrent CompletableFuture)
           (java.nio ByteBuffer)
           (java.nio.channels ReadableByteChannel)
           (org.eclipse.jetty.util Callback)
           (org.eclipse.jetty.server HttpOutput)
           (org.eclipse.jetty.server Response)
           (java.io OutputStreamWriter File InputStream)))

(defprotocol ResponseWriter
  (set-status! [response status])
  (set-headers! [response headers])
  (send-error! [response ^Throwable error]))

(defprotocol BodyWriter
  (set-body! [response body]))

(defprotocol BodyWriterAsync
  (set-body-async! [response body]))

(defprotocol WriteListener
  (set-write-listener! [response listener]))

(defprotocol WriteBody
  (write-body! [body response]))

(defprotocol WriteBodyAsync
  (write-body-async! [body response]))

(extend-type Response
  ResponseWriter
  (set-status! [response status]
    (.setStatus response status))

  (set-headers! [response headers]
    (run! (fn [[key val-or-vals]]
            (if (string? val-or-vals)
              (.setHeader response
                          ^String key
                          ^String val-or-vals)
              (run! #(.addHeader response key %)
                    val-or-vals)))
          headers))

  (send-error! [response e]
    (.sendError response 500 (ex-message e)))

  BodyWriter
  (set-body! [response body]
    (write-body! body response))

  BodyWriterAsync
  (set-body-async! [response body]
    (write-body-async! body response))

  (set-write-listener! [response listener]
    (-> response (.getOutputStream) (.setWriteListener listener))))

(extend-protocol WriteBody

  (Class/forName "[B")
  (write-body! [body ^Response response]
    (let [output-stream (.getOutputStream response)]
      (.write output-stream ^bytes body)
      (.close output-stream)))

  nil
  (write-body! [body ^Response response]
    (let [output-stream (.getOutputStream response)]
      (.close output-stream)))

  String
  (write-body! [s ^Response response]
    (let [output-stream (.getOutputStream response)
          writer (OutputStreamWriter. output-stream)]
      (.write writer s)
      (.flush writer)))

  clojure.lang.Fn
  (write-body! [f ^Response response]
    (let [output-stream (.getOutputStream response)]
      (f output-stream)))

  ByteBuffer
  (write-body! [bb ^Response response]
    (let [output (.getHttpOutput response)]
      (.sendContent output bb)))

  ReadableByteChannel
  (write-body! [rbc ^Response response]
    (let [output (.getHttpOutput response)]
      (.sendContent output rbc)))

  File
  (write-body! [file ^Response response]
    (let [output-stream (.getOutputStream response)]
      (io/copy file output-stream)))

  InputStream
  (write-body! [ios ^Response response]
    (.sendContent ^HttpOutput (.getHttpOutput response)
                  ios))

  clojure.lang.ISeq
  (write-body! [xs ^Response response]
    (let [output-stream (.getOutputStream response)
          writer (OutputStreamWriter. output-stream)]
      (doseq [chunk xs]
        (.write writer (str chunk)))
      (.close writer))))

(extend-protocol WriteBodyAsync
  InputStream
  (write-body-async! [ios ^Response response]
    (let [cf ^CompletableFuture (ax/future)]
      (.sendContent ^HttpOutput (.getHttpOutput response)
                    ios
                    ^Callback (Callback/from cf))
      cf))

  ByteBuffer
  (write-body-async! [bb ^Response response]
    (let [cf ^CompletableFuture (ax/future)]
      (.sendContent ^HttpOutput (.getHttpOutput response)
                    bb
                    ^Callback (Callback/from cf))
      cf))

  ReadableByteChannel
  (write-body-async! [bc ^Response response]
    (let [cf ^CompletableFuture (ax/future)]
      (.sendContent ^HttpOutput (.getHttpOutput response)
                    bc ^Callback
                    (Callback/from cf))
      cf))

  CompletableFuture
  (write-body-async! [f ^Response response]
    (let [cf ^CompletableFuture (ax/future)]
      (-> f
          (ax/fmap (fn [body] (write-body-async! body response)))
          (ax/then cf (fn [_] (ax/success! cf nil)))
          (ax/catch (fn [e] (ax/error! cf e))))
      cf))

  ;; ;; file
  ;; ;; https://github.com/eclipse/jetty.project/blob/b56edf511ab4399122ea2c6162a4a5988870f479/demos/embedded/src/main/java/org/eclipse/jetty/demos/FastFileServer.java

  String
  (write-body-async! [s response]
    (write-body-async! (ByteBuffer/wrap (.getBytes s "UTF-8"))
                       response))

  nil
  (write-body-async! [_ ^Response response]
    (let [cf ^CompletableFuture (ax/future)]
      (.complete ^HttpOutput (.getHttpOutput response)
                 ^Callback (Callback/from cf))
      cf)))
