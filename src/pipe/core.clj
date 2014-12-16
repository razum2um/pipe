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

(defonce server (atom nil))
(defonce target-host (atom nil))
(defonce transformators (atom nil))

(defmulti pretty-xml class)

(defmethod pretty-xml nil [_] nil)

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
        resp-body (resp-decorator-fn body)
        ;; _ (timbre/debug "upstream resp status=" status "\nheaders=" headers "\nbody=" body "\n\n")
        resp {:status status
              :headers resp-headers
              :body resp-body
              }]
    (send! out-ch resp)))

(defn upstream-request [req-decorator-fn resp-decorator-fn channel request-method scheme uri headers body]
  (let [
        upstream-body (req-decorator-fn body)
        ;; _ (timbre/debug "upstream req meth=" request-method "\nuri=" uri "\nheaders=" headers "\nbody=" body "\n\n")
        upstream-req {:url (str (name scheme) "://" @target-host uri)
                      :method request-method
                      :as :text
                      :headers (assoc headers "host" @target-host)
                      :body upstream-body
                      }]
    (http/request upstream-req
                  (partial upstream-handler resp-decorator-fn channel))))

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
    (reset! transformators nil)
    (reset! target-host nil)
    (reset! server nil)))

(defmulti ensure-pair coll?)
(defmethod ensure-pair true [xs] (take 2 (cycle xs)))
(defmethod ensure-pair false [x] [x x])

(defn start [host & fn-pairs]
  (let [req-resp-fn-pairs (map ensure-pair (conj fn-pairs identity))
        ;; _ (timbre/debug "req-resp-fn-pairs=" req-resp-fn-pairs "\n")
        req-fns-resp-fns (apply map vector req-resp-fn-pairs)
        ;; _ (timbre/debug "req-fns-resp-fns=" req-fns-resp-fns "\n")
        req-resp-fns (->> req-fns-resp-fns
                          (map #(->> % reverse (apply comp)))
                          vec)
        ;; _ (timbre/debug "req-resp-fns=" req-resp-fns "\n")
        ]
    (reset! transformators req-resp-fns)
    (reset! target-host host)
    (reset! server (run-server #'handler {:port 8080}))))

(defn -main
  [& args]
  (start (or (first args)
             (throw (Throwable. "pass a host as an argument like: 'ya.ru'")))))

