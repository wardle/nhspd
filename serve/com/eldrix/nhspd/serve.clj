(ns com.eldrix.nhspd.serve
  (:require [com.eldrix.nhspd.api :as nhspd]
            [clojure.data.json :as json]
            [clojure.tools.logging.readable :as log]
            [io.pedestal.http :as http]
            [io.pedestal.http.content-negotiation :as conneg]
            [io.pedestal.http.route :as route]
            [io.pedestal.interceptor :as intc]
            [ring.util.response :as ring-response])
  (:import (java.net URLDecoder)))

(set! *warn-on-reflection* true)

(def supported-types ["application/json" "application/edn" "text/plain"])
(def content-neg-intc (conneg/negotiate-content supported-types))

(defn response [status body & {:as headers}]
  {:status  status
   :body    body
   :headers headers})

(def ok (partial response 200))
(def not-found (partial response 404))

(defn accepted-type
  [ctx]
  (get-in ctx [:request :accept :field] "application/json"))

(defn transform-content
  [body content-type]
  (case content-type
    "text/plain" body
    "application/edn" (pr-str body)
    "application/json" (json/write-str body)))

(defn coerce-to
  [response content-type]
  (-> response
      (update :body transform-content content-type)
      (assoc-in [:headers "Content-Type"] content-type)))

(def coerce-body
  {:name ::coerce-body
   :leave
   (fn [ctx]
     (if (get-in ctx [:response :headers "Content-Type"])
       ctx
       (update-in ctx [:response] coerce-to (accepted-type ctx))))})

(defn inject-svc
  "A simple interceptor to inject service 'svc' into the context."
  [svc]
  {:name  ::inject-svc
   :enter (fn [ctx] (assoc ctx ::svc svc))})

(def entity-render
  "Interceptor to render an entity '(:result context)' into the response."
  {:name :entity-render
   :leave
   (fn [{:keys [result] :as ctx}]
     (if result
       (assoc ctx :response (ok result))
       ctx))})

(def get-postcode
  {:name  ::get-postcode
   :enter (fn [{::keys [svc] :as ctx}]
            (when-let [pc (get-in ctx [:request :path-params :postcode])]
              (let [pc' (URLDecoder/decode ^String pc "UTF-8")]
                (if-let [postcode (nhspd/fetch-postcode svc pc')]
                  (assoc ctx :result postcode)
                  (assoc ctx :response (ring-response/not-found "Not Found"))))))})

(def common-routes [coerce-body content-neg-intc entity-render])
(def routes
  (route/expand-routes
    #{["/v1/nhspd/:postcode" :get (conj common-routes get-postcode)]}))

(def service-map
  {::http/routes routes
   ::http/type   :jetty
   ::http/port   8082})

(defn create-server [nhspd-svc port join?]
  (http/create-server (-> service-map
                          (assoc ::http/port port)
                          (assoc ::http/join? join?)
                          (http/default-interceptors)
                          (update ::http/interceptors conj (intc/interceptor (inject-svc nhspd-svc))))))

(defn start-server
  [nhspd-svc port]
  (if-not (= "CF14 4XW" (get (nhspd/fetch-postcode nhspd-svc "CF14 4XW") "PCD2"))
    (do (log/error "Uninitialised index.")
        (System/exit 1))
    (do (log/info "starting server on port " port)
        (http/start (create-server nhspd-svc port true)))))

(defn stop-server [server]
  (http/stop server))

(defn -main [& args]
  (if-not (= 2 (count args))
    (println "Incorrect parameter. Usage: clj -M:serve <index> <port>")
    (let [svc (nhspd/open (first args))
          port (Integer/parseInt (second args))]
      (start-server svc port))))

;; For interactive development
(defonce server (atom nil))

(defn start-dev [nhspd-svc port]
  (reset! server
          (http/start (create-server nhspd-svc port false))))

(defn stop-dev []
  (http/stop @server))

(comment
  (def nhspd (nhspd/open "nhspd.db"))
  (nhspd/fetch-postcode nhspd "CF14 4XW")
  (start-dev nhspd 3000)
  (stop-dev))

