(ns com.eldrix.nhspd.core
  "Support for the NHS Postcode directory (NHSPD)."
  (:require [com.eldrix.nhspd.download :as dl]
            [com.eldrix.nhspd.search :as search]
            [clojure.core.async :as a]
            [next.jdbc :as jdbc])
  (:import (org.apache.lucene.search IndexSearcher)
           (java.io Closeable)
           (java.time.format DateTimeFormatter)))

(defprotocol NHSPD
  "The NHSPD service provides facilities for managing the NHS postcode directory."
  (fetch-postcode [this pc] "Fetch the raw data from NHSPD.")
  (fetch-wgs84 [this pc] "Fetch grid coordinates (WGS84 lat/long) for a postcode."))

(defn open-index
  "Open an NHSPD index from the directory specified.
  Wrap in `with-open` to close the index when done."
  [dir]
  (let [reader (search/open-index-reader dir)
        searcher (IndexSearcher. reader)]
    (reify
      NHSPD
      (fetch-postcode [_ pc] (search/fetch-postcode searcher pc))
      (fetch-wgs84 [_ pc] (search/fetch-wgs84 searcher pc))
      Closeable
      (close [_] (.close reader)))))

(defn write-index
  "Write an NHSPD file-based database.
  The NHSPD will be automatically downloaded or the release files from `in`
  will be used if given.
  Parameters:
  - dir : directory in which to build the index
  - in  : java.io.File or filename of the NHSPD zip file."
  ([]
   (let [dir (str "nhspd-" (.format (:date (dl/get-latest-release)) DateTimeFormatter/ISO_DATE) ".db")]
     (write-index dir)))
  ([dir]
   (let [ch (a/chan 1 (partition-all 10000))]
     (a/thread (dl/stream-latest-release ch))
     (search/build-index ch dir)))
  ([dir in]
   (let [ch (a/chan 1 (partition-all 10000))]
     (a/thread (dl/stream-release in ch))
     (search/build-index ch dir))))

(defn -main [& args]
  (case (count args)
    0 (write-index)
    1 (write-index (first args))
    (println "Usage: nhspd <dir> where dir is index directory (e.g. /var/nhspd")))

(comment
  (def nhspd (open-index "/var/tmp/nhspd-nov-2020"))
  (fetch-postcode nhspd "cf144xw")
  (.close nhspd)
  (write-index "/var/tmp/nhspd-nov-2020")
  (with-open [nhspd (open-index "/var/tmp/nhspd-nov-2020")]
    (fetch-postcode nhspd "CF14 4XW"))

  (write-index "/var/tmp/nhspd-nov-2020--2" "/Users/mark/Downloads/nhspd-nov-2020.zip")
  (with-open [nhspd (open-index "/var/tmp/nhspd-nov-2020--2")]
    (fetch-postcode nhspd "CF14 4XW")))

(defn postcode->data
  [{:strs [PCD2 PCDS OSNRTH1M OSEAST1M] :as pc}]
  (vector PCD2 PCDS OSNRTH1M OSEAST1M (taoensso.nippy/fast-freeze pc)))

["PCD2" "PCDS" "DOINTR" "DOTERM" "OSEAST100M"
 "OSNRTH100M" "OSCTY" "ODSLAUA" "OSLAUA" "OSWARD"
 "USERTYPE" "OSGRDIND" "CTRY" "OSHLTHAU" "RGN"
 "OLDHA" "NHSER" "CCG" "PSED" "CENED"
 "EDIND" "WARD98" "OA01" "NHSRLO" "HRO"
 "LSOA01" "UR01IND" "MSOA01" "CANNET" "SCN"
 "OSHAPREV" "OLDPCT" "OLDHRO" "PCON" "CANREG"
 "PCT" "OSEAST1M" "OSNRTH1M" "OA11" "LSOA11"
 "MSOA11" "CALNCV" "STP"]

(defn write-batch
  [conn batch]
  (jdbc/execute-batch! conn
                       "insert into nhspd (pcd2, pcds, osnrth1m, oseast1m, data) values (?,?,?,?,?)"
                       (map postcode->data batch) {}))

(comment

  (def ch (async/chan 1 (comp (map postcode->data) (partition-all 50))))
  (require '[next.jdbc :as jdbc])
  (require '[clojure.core.async :as async])
  (def conn (jdbc/get-connection "jdbc:sqlite:nhspd.db"))
  (async/thread (dl/stream-latest-release ch))
  (def batch (async/<!! ch))
  (let [{:strs [PCD2 PCDS OSNRTH1M OSEAST1M]} (first batch)]
    {:PCD2 PCD2 :PCDS PCDS :n OSNRTH1M :s OSEAST1M})
  (taoensso.nippy/fast-freeze (first batch))
  (jdbc/execute-one! conn ["create table if not exists nhspd (pcds text, pcd2 text, osnrth1m integer, oseast1m integer, data blob)"])
  (loop [batch (async/<!! ch)]
    (when batch
      (write-batch conn batch)
      (recur (async/<!! ch)))))





