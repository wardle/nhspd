(ns com.eldrix.nhspd.cmd
  (:gen-class)
  (:require
    [clojure.data.json :as json]
    [clojure.java.io :as io]
    [clojure.pprint :as pp]
    [clojure.string :as str]
    [com.eldrix.nhspd.api :as nhspd]
    [com.eldrix.nhspd.impl.cli :as cli]
    [com.eldrix.nhspd.impl.serve :as serve])
  (:import (java.time LocalDate LocalDateTime)
           (java.time.format DateTimeFormatter)))

(defn serve
  [{:keys [db port bind allowed-origin] :as opts} _args]
  (let [svc (nhspd/open db)
        allowed-all (contains? (set allowed-origin) "*")]
    (println "Running HTTP server " opts)
    (serve/start-server svc (cond-> {:port port}
                              bind (assoc :host bind)
                              allowed-all (assoc :allowed-origins (constantly true))
                              (and allowed-origin (not allowed-all)) (assoc :allowed-origins allowed-origin)))))


(defn write-local-date-time [^LocalDateTime o ^Appendable out _options]
  (.append out \")
  (.append out (.format DateTimeFormatter/ISO_DATE_TIME o))
  (.append out \"))


(defn write-local-date [^LocalDate o ^Appendable out _options]
  (.append out \")
  (.append out (.format DateTimeFormatter/ISO_DATE o))
  (.append out \"))

(extend LocalDateTime json/JSONWriter {:-write write-local-date-time})
(extend LocalDate json/JSONWriter {:-write write-local-date})

(defn truncate
  [s n]
  (let [c (count s)]
    (if (> c n)
      (str (subs s 0 (- n 3)) "...")
      s)))

(defn status-text [{:keys [n manifests columns version]}]
  (println "\n\n")
  (println "NHSPD schema version : " version)
  (println "Number of postcodes  :" n)
  (println "\nMANIFESTS:")
  (pp/print-table manifests)
  (println "\n\nNHSPD COLUMNS (FIELDS):")
  (pp/print-table [:name :description]
                  (map (fn [col] (update col :description #(truncate % 70))) columns)))

(defn status
  [{:keys [db format] :or {format :text}} _args]
  (if (.exists (io/file db))
    (with-open [svc (nhspd/open db)]
      (let [data (nhspd/status svc)]
        (case format
          :text (status-text data)
          :edn (prn data)
          :json (json/write data *out*))))
    (println "ERROR: index file not found:" db)))

(defn create-db
  [{:keys [db profile column]} _args]
  (if (.exists (io/file db))
    (println "ERROR: index file already exists:" db)
    (do
      (println "Creating new NHSPD index file from latest release:" db)
      (nhspd/create-latest db {:profile profile :cols column}))))

(defn update-db
  [{:keys [db delay]} _args]
  (if-not (.exists (io/file db))
    (println "ERROR: existing index file not found:" db)
    (let [svc (nhspd/open db)]
      (println "Updating index file to latest release: " db)
      (nhspd/update-from-latest svc {:delay delay}))))

(defn import-files
  [{:keys [db profile column release delay]} args]
  (if (.exists (io/file db))
    (do (println "importing files into existing database")
        (nhspd/create-from-files db args {:profile profile :cols column :release release}))
    (let [svc (nhspd/open db)]
      (println "creating new database from files")
      (nhspd/update-from-files svc args {:release release :delay delay}))))

(def commands
  {"create" create-db
   "update" update-db
   "status" status
   "import" import-files
   "serve"  serve})

(defn -main [& args]
  (let [{:keys [cmd options arguments errors usage]}
        (cli/parse-args args)]
    (cond
      (seq errors)
      (do (doseq [error errors] (println "ERROR: " error))
          (println usage))
      (:help options)
      (println usage)
      (nil? (:db options))
      (do (println "ERROR: missing option --db\n")
          (println usage))
      (and (= "import" cmd) (empty? arguments))
      (do (println "ERROR: no input files specified\n")
          (println usage))
      (and (not= "import" cmd) (seq arguments))
      (do (println "ERROR: extraneous input:" (str/join " " arguments))
          (println usage))
      :else
      ((get commands cmd) options arguments))))


