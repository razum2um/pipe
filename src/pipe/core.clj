(ns pipe.core
  (:require [org.httpkit.client :as http]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as s :refer [trim]]
            [clojure.walk :as walk]
            [org.httpkit.server :refer :all]
            [clojure.java.io :as io]
            [taoensso.timbre :as timbre])
  (:import [javax.xml.transform Transformer TransformerFactory]
           [javax.xml.transform.stream StreamSource StreamResult]
           [java.io StringReader StringWriter])
  (:gen-class))

(defonce server (atom ""))
(defonce target-host (atom ""))
(defonce transformators (atom []))
(defonce cache (atom {}))

(defmulti pretty-xml (comp class last list))

(defmethod pretty-xml nil [_] nil)

(defmethod pretty-xml javax.xml.transform.stream.StreamSource [indent in]
  (let [out (StreamResult. (StringWriter.))
        tf (TransformerFactory/newInstance)
        t (doto (.newTransformer tf)
            (.setOutputProperty "indent" "yes")
            (.setOutputProperty "{http://xml.apache.org/xslt}indent-amount" (str indent))
            (.transform in out))]
    (-> out .getWriter .toString)))

(defmethod pretty-xml org.httpkit.BytesInputStream [indent s]
  (let [in (StreamSource. s)]
    (pretty-xml indent in)))

(defmethod pretty-xml java.lang.String [indent s]
  (let [r (StringReader. s)
        in (StreamSource. r)]
    (pretty-xml indent in)))

(defmulti inspect (comp class last list))

(defmethod inspect org.httpkit.BytesInputStream [wrapper-fn s]
  (with-open [rdr (io/reader s)]
    (inspect wrapper-fn (s/join "\n" (line-seq rdr))))
  (.reset s)
  s)

(defmethod inspect :default [wrapper-fn s]
  (wrapper-fn s)
  s)

(defn truncate [s]
  (if (< 1000 (count s))
    (apply str (concat (take 1000 s) (take 3 (repeat "."))))
    s))

(defn prefix-log [prefix]
  (let [logfn (fn [s] )])
  (partial inspect #(->> % truncate (timbre/info prefix))))

;; #########

(defn upstream-handler [resp-decorator-fn out-ch upstream-resp]
  (let [{:keys [status headers body error opts]} upstream-resp
        resp-headers (walk/stringify-keys
                       (dissoc headers
                               :content-encoding
                               :x-xrds-location
                               :content-security-policy
                               :transfer-encoding
                               :x-frame-options))
        resp {:status status
              :headers resp-headers
              :body body}
        decorated-resp (update-in resp [:body] resp-decorator-fn)]
    (swap! cache assoc opts resp)
    (send! out-ch decorated-resp)))

(defn upstream-request [req-decorator-fn resp-decorator-fn out-ch request-method scheme uri headers body]
  (let [
        upstream-body (req-decorator-fn body)
        url (str (name scheme) "://" @target-host uri)
        upstream-req {:url url
                      :method request-method
                      :as :text
                      :headers (assoc headers "host" @target-host)
                      :body upstream-body}
        cached-resp (get @cache upstream-req)
        ]
    (if cached-resp
      (do (timbre/debug "Cached:" url)
          (send! out-ch (update-in cached-resp [:body] resp-decorator-fn)))
      (http/request upstream-req
                    (partial upstream-handler resp-decorator-fn out-ch)))))

(defn single-arity? [v]
  (some #(= 1 (count %))
        (-> v meta :arglists)))

(defn handler [req]
  (let [[req-decorator-var resp-decorator-var] @transformators]
    (with-channel req channel
      (let [{:keys [request-method scheme uri headers body]} req]
        (upstream-request req-decorator-var resp-decorator-var channel
                          request-method scheme uri headers body)))))

(defn stop []
  (when @server
    (@server :timeout 100)
    (reset! cache {})
    (reset! transformators nil)
    (reset! target-host nil)
    (reset! server nil)))

(defmulti ensure-pair coll?)
(defmethod ensure-pair true [xs] (take 2 (cycle xs)))
(defmethod ensure-pair false [x] [x x])

(defn response-chain [& xs]
  (map vector (repeat identity) xs))

(defn request-chain [& xs]
  (map vector xs (repeat identity)))

(defn start [host fn-pairs]
  (let [req-resp-fn-pairs (map ensure-pair (conj fn-pairs identity))
        req-fns-resp-fns (apply map vector req-resp-fn-pairs)
        req-resp-fns (->> req-fns-resp-fns
                          (map #(->> % reverse (apply comp)))
                          vec)]
    (reset! cache {})
    (reset! transformators req-resp-fns)
    (reset! target-host host)
    (reset! server (run-server #'handler {:port 8080}))))

(defn -main
  [& args]
  (start (or (first args)
             (throw (Throwable. "pass a host as an argument like: 'ya.ru'")))))

