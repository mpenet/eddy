(ns s-exp.jetty.test.server-test
  (:require [clojure.test :refer [deftest is]]
            [s-exp.jetty.http.server :as srv]
            [s-exp.jetty.http.server.response :as rsp]
            [s-exp.jetty.http.server.protocols :as p]
            [clj-http.client :as http]
            [s-exp.jetty.http.interceptor.ring1 :as ring1]
            [qbits.auspex :as ax]))

(def ^:dynamic *server* nil)
(def ^:dynamic *response* (atom nil))

(defmacro with-server [opts & body]
  `(binding [*server* (srv/start! ~opts)]
     (try
       ~@body
       (finally
         (srv/stop! *server*)))))

(deftest test-simple-get-req
  (with-server {:s-exp.jetty.http.server/join? false
                :s-exp.jetty.http.server.interceptor/ctx
                {:ring1/handler (fn [request] {})}}
    (is (-> (http/get "http://localhost:8080") :status (= 200)) ))

  (with-server {:s-exp.jetty.http.server/join? false
                :s-exp.jetty.http.server.interceptor/ctx
                {:ring1/handler (fn [request] {:status 200})}}
    (is (-> (http/get "http://localhost:8080") :status (= 200))))

  (with-server {:s-exp.jetty.http.server/join? false
                :s-exp.jetty.http.server.interceptor/ctx
                {:ring1/handler (fn [request] {:status 200 :body "test"})}}
    (is (-> (http/get "http://localhost:8080") :status (= 200))))

  (with-server {:s-exp.jetty.http.server/join? false
                :s-exp.jetty.http.server.interceptor/ctx
                {:ring1/handler (fn [request] {:status 201})}}
    (is (-> (http/get "http://localhost:8080") :status (= 201)))))


(deftest test-simple-post-req
  (with-server {:s-exp.jetty.http.server/join? false
                :s-exp.jetty.http.server.interceptor/ctx
                {:ring1/handler (fn [request] {})}}
    (is (-> (http/post "http://localhost:8080") :status (= 200)) ))

  (with-server {:s-exp.jetty.http.server/join? false
                :s-exp.jetty.http.server.interceptor/ctx
                {:ring1/handler (fn [request] {:body (:body request)})}}
    (is (-> (http/post "http://localhost:8080"
                       {:body "something"})
            :body (= "something"))))

  (with-server {:s-exp.jetty.http.server/join? false
                :s-exp.jetty.http.server.interceptor/ctx
                {:ring1/handler (fn [request] {:body (:body request)})}}
    (let [large-body (apply str (range 100000))]
      (is (-> (http/post "http://localhost:8080"
                         {:body large-body})
              :body (= large-body))))))

(deftest async-test
  (with-server {:s-exp.jetty.http.server/join? false
                :s-exp.jetty.http.server.interceptor/chain ring1/async-chain
                :s-exp.jetty.http.server.interceptor/ctx
                {:ring1/handler (fn [request] (ax/success-future {:status 200}))}}
    (is (-> (http/get "http://localhost:8080")
            :status (= 200))))

  (with-server {:s-exp.jetty.http.server/join? false
                :s-exp.jetty.http.server.interceptor/chain ring1/async-chain
                :s-exp.jetty.http.server.interceptor/ctx
                {:ring1/handler (fn [request]
                                  (ax/success-future {:status 201 :body "test"}))}}
    (is (-> (http/get "http://localhost:8080")
            :status (= 201)))
    (is (-> (http/get "http://localhost:8080")
            :body (= "test"))))

  (with-server {:s-exp.jetty.http.server/join? false
                :s-exp.jetty.http.server.interceptor/chain ring1/async-chain
                :s-exp.jetty.http.server.interceptor/ctx
                {:ring1/handler (fn [request]
                                  (ax/success-future {:status 201
                                                      :body (ax/success-future "test async body")}))}}
    (is (-> (http/get "http://localhost:8080")
            :status (= 201)))
    (is (-> (http/get "http://localhost:8080")
            :body (= "test async body")))))

(deftest fast-test
  (with-server {:s-exp.jetty.http.server/join? false
                :s-exp.jetty.http.server/handler
                (fn [opts]
                  (srv/create-handler
                   (fn [_ s-request s-response]
                     (p/set-handled! s-request true)
                     (p/set-status! s-response 200))))}
    (is (-> (http/get "http://localhost:8080")
            :status (= 200)))))
