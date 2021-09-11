(ns com.eldrix.nhspd.serve
      (:require [com.eldrix.nhspd.core :as nhspd]
                [cheshire.core :as json]
                [clojure.tools.logging.readable :as log]
                [io.pedestal.http :as http]
                [io.pedestal.http.content-negotiation :as conneg]
                [io.pedestal.http.route :as route]
                [io.pedestal.interceptor :as intc]
                [ring.util.response :as ring-response]))

(set! *warn-on-reflection* true)

(def supported-types ["application/json"  "application/edn" "text/plain"])
(def content-neg-intc (conneg/negotiate-content supported-types))

(defn response [status body & {:as headers}]
  {:status  status
   :body    body
   :headers headers})

(def ok (partial response 200))
(def not-found (partial response 404))

(defn accepted-type
  [context]
  (get-in context [:request :accept :field] "application/json"))

(defn transform-content
  [body content-type]
  (case content-type
    "text/plain" body
    "application/edn" (pr-str body)
    "application/json" (json/generate-string body)))

(defn coerce-to
  [response content-type]
  (-> response
      (update :body transform-content content-type)
      (assoc-in [:headers "Content-Type"] content-type)))

(def coerce-body
  {:name ::coerce-body
   :leave
   (fn [context]
     (if (get-in context [:response :headers "Content-Type"])
       context
       (update-in context [:response] coerce-to (accepted-type context))))})

(defn inject-svc
  "A simple interceptor to inject service 'svc' into the context."
  [svc]
  {:name  ::inject-svc
   :enter (fn [context] (update context :request assoc :com.eldrix.nhspd/svc svc))})

(def entity-render
  "Interceptor to render an entity '(:result context)' into the response."
  {:name :entity-render
   :leave
   (fn [context]
     (if-let [item (:result context)]
       (assoc context :response (ok item))
       context))})

(def get-postcode
  {:name  ::get-postcode
   :enter (fn [context]
            (when-let [pc (get-in context [:request :path-params :postcode])]
              (let [nhspd-svc (get-in context [:request :com.eldrix.nhspd/svc])
                    pc' (java.net.URLDecoder/decode ^String pc "UTF-8")]
              (if-let [postcode (nhspd/fetch-postcode nhspd-svc pc')]
                (assoc context :result postcode)
                (assoc context :response (ring-response/not-found "Not Found"))))))})

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
    (println "Incorrect parameter. Usage: clj -M:serve <index directory> <port>")
    (let [svc (nhspd/open-index (first args))
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
  (def nhspd (nhspd/open-index "/tmp/nhspd-2021-02"))
  (nhspd/fetch-postcode nhspd "CF14 4XW")
  (start-dev nhspd 3000)
  (stop-dev)

  )