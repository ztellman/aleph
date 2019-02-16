(ns aleph.sni-test
  (:use [clojure test])
  (:require
   [aleph.tcp-ssl-test :as ssl-test]
   [aleph.http-test :refer [with-server]]
   [aleph.http :as http]
   [aleph.netty :as netty])
  (:import
   [io.aleph.dirigiste IPool]))

(def port 8092)

(defn ok-handler [_]
  {:status 200
   :body "OK"})

(defmacro with-sni-handler [handler & body]
  `(let [ssl# ssl-test/server-ssl-context
         default-ssl# (netty/self-signed-ssl-context)]
     (with-server (http/start-server ~handler
                                     {:port port
                                      :ssl-context {"aleph.io.local" ssl#
                                                    "*" default-ssl#}})
       ~@body)))

(deftest test-sni-handler
  (let [pool1 (http/connection-pool
               {:connection-options
                {:sni {:host "aleph.io.local"}
                 :ssl-context ssl-test/client-ssl-context}})
        pool2 (http/connection-pool
               {:connection-options
                {:ssl-context ssl-test/client-ssl-context}})]
    (with-sni-handler ok-handler
      (testing "succcess on known host"
        (let [resp (http/get (str "https://aleph.io.local:" port)
                             {:pool pool1})]
          (is (= 200 (:status @resp)))))

      ;; do not specify SNI host manually
      ;; self-signed should not be trusted
      (testing "fails on unrecognized host"
        (is (thrown? Exception
                     @(http/get (str "https://127.0.0.1:" port)
                                {:pool pool2})))))
    (.shutdown ^IPool pool1)
    (.shutdown ^IPool pool2)))
