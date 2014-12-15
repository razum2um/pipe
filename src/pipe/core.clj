(ns pipe.core
  (:require [org.httpkit.client :as http]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as s :refer [trim]]
            [clojure.walk :as walk]
            [org.httpkit.server :refer :all]
            [taoensso.timbre :as log])
  (:import [javax.xml.transform Transformer TransformerFactory]
           [javax.xml.transform.stream StreamSource StreamResult]
           [java.io StringReader StringWriter])
  (:gen-class))

(defonce server (atom nil))
(defonce target-host (atom nil))

(defmulti pretty-xml class)

(defmethod pretty-xml javax.xml.transform.stream.StreamSource [in]
  (let [out (StreamResult. (StringWriter.))
        tf (TransformerFactory/newInstance)
        t (doto (.newTransformer tf)
            (.setOutputProperty "indent" "yes")
            (.setOutputProperty "{http://xml.apache.org/xslt}indent-amount" "2")
            (.transform in out))]
    (-> out .getWriter .toString)))

(defmethod pretty-xml org.httpkit.BytesInputStream [s]
  (let [in (StreamSource. s)]
    (pretty-xml in)))

(defmethod pretty-xml java.lang.String [s]
  (let [r (StringReader. s)
        in (StreamSource. r)]
    (pretty-xml in)))


(defn upstream-handler [out-ch upstream-resp]
  (let [{:keys [status headers body error opts]} upstream-resp
        resp-headers (walk/stringify-keys
                       (dissoc headers
                               :content-encoding
                               :x-xrds-location
                               :content-security-policy
                               :transfer-encoding
                               :x-frame-options))
        _ (log/debug "Upstream headers:\n" (-> headers pprint with-out-str trim))
        resp-body (pretty-xml body)
        _ (log/info "Out:\n" (trim resp-body))
        resp {:status status
              :headers resp-headers
              :body resp-body}]
    (send! out-ch resp)))

(defn async-proxy [channel request-method scheme uri headers body]
  (let [upstream-body (pretty-xml body)
        _ (log/info "In:\n" (trim upstream-body))
        upstream-req {:url (str (name scheme) "://" @target-host uri)
                      :method request-method
                      :as :text
                      :headers (assoc headers "host" @target-host)
                      :body upstream-body}]
    (http/request upstream-req
                  (partial upstream-handler channel))))

(defn handler [req]
  (with-channel req channel
    (let [{:keys [request-method scheme uri headers body]} req]
      (async-proxy channel request-method scheme uri headers body))))

(defn stop []
  (when-not (nil? @server)
    (@server :timeout 100)
    (reset! target-host nil)
    (reset! server nil)))

(defn start
  ([host] (start host 8080))
  ([host port]
   (reset! target-host host)
   (reset! server (run-server #'handler {:port port}))))

(defn -main
  [& args]
  (start (or (first args)
             (throw (Throwable. "pass a host as an argument like: 'ya.ru'")))))

