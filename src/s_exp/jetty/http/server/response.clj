(ns s-exp.jetty.http.server.response
  (:require [s-exp.jetty.http.server.protocols :as p]
            [clojure.java.io :as io]
            [qbits.auspex :as ax])
  (:import (java.util.concurrent CompletableFuture)
           (java.nio ByteBuffer)
           (java.nio.channels ReadableByteChannel)
           (org.eclipse.jetty.util Callback)
           (org.eclipse.jetty.server HttpOutput)
           (jakarta.servlet AsyncContext)
           (org.eclipse.jetty.server Response)
           (java.io OutputStreamWriter File InputStream)))

(extend-type Response
  p/Response

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

  p/BodyWriter
  (set-body! [response body]
    (p/write-body! body response))

  p/BodyWriterAsync
  (set-body-async! [response body]
    (p/write-body-async! body response)))

(extend-protocol p/WriteBody

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
    (let [output-stream (.getOutputStream response)]
      (with-open [ios ios]
        (io/copy ios output-stream))
      (.close output-stream)))

  clojure.lang.ISeq
  (write-body! [xs ^Response response]
    (let [output-stream (.getOutputStream response)
          writer (OutputStreamWriter. output-stream)]
      (doseq [chunk xs]
        (.write writer (str chunk)))
      (.close writer))))

(extend-protocol p/WriteBodyAsync
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
          (ax/fmap (fn [body] (p/write-body-async! body response)))
          (ax/then cf (fn [_] (ax/success! cf nil)))
          (ax/catch (fn [e] (ax/error! cf e))))
      cf))

  ;; ;; file
  ;; ;; https://github.com/eclipse/jetty.project/blob/b56edf511ab4399122ea2c6162a4a5988870f479/demos/embedded/src/main/java/org/eclipse/jetty/demos/FastFileServer.java

  String
  (write-body-async! [s response]
    (p/write-body-async! (ByteBuffer/wrap (.getBytes s "UTF-8"))
                         response))

  nil
  (write-body-async! [s response]
    (ax/success-future nil)))
