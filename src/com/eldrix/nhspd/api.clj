(ns com.eldrix.nhspd.api
  "The NHS Postcode Directory (NHSPD) relates both current and terminated postcodes in the United
Kingdom to a range of current statutory administrative, electoral, health and other area geographies.
It also links postcodes to pre-2002 health areas, 1991 Census enumeration districts (for England and
Wales) and both 2001 Census and 2011 Census Output Areas and Super Output Areas. It helps support
the production of area-based statistics from postcoded data. The NHSPD is produced by ONS
Geography, who provide geographic support to the Office for National Statistics (ONS) and geographic
services used by other organisations. They issue the NHSPD quarterly. "
  (:require
    [clojure.core.async :as async]
    [com.eldrix.nhspd.impl.coords :as coords]
    [com.eldrix.nhspd.impl.download :as dl]
    [com.eldrix.nhspd.impl.store :as store]
    [com.eldrix.nhspd.postcode :as pc])
  (:import (java.io Closeable)))

(defrecord ^:private NHSPD [ds]
  Closeable
  (close [_]))

(defn open
  "Open an existing NHSPD service"
  ^Closeable [f]
  (->NHSPD (store/open-db f)))

(defn close [svc]
  (.close svc))

(defn postcode
  "Return NHSPD data for the given postcode. Returns data as map of keyword keys
  to values."
  [svc s]
  (store/postcode (.-ds svc) s))

(defn ^:deprecated fetch-postcode
  "DEPRECATED: use [[postcode]] instead. Returns NHSPD data for given postcode
  with result as map, but unlike [[postcode]], returns data with *string* keys.
  This is only for backwards compatibility with older versions of the library."
  [svc s]
  (store/fetch-postcode (.-ds svc) s))

(defn os-grid-reference
  "Return Ordnance Survey grid reference data for a postcode.
  Parameters:
  - s : either a full postcode, or prefix.
  For a prefix, the average location of all postcodes with that prefix will be
  returned."
  [svc s]
  (store/os-grid-reference (.-ds svc) s))

(defn with-wgs84
  "Add :WGS84LAT and :WGS84LNG to the NHSPD data based on the Ordnance Survey
  easting/northing coordinates."
  [pc]
  (coords/with-wgs84 pc))

(defn distance-between
  "Return the distance in metres between two UK postal codes. This works for
  full postcodes and prefixes.
  For example
  ```
  (distance-between svc \"CF14 2HB\" \"CF14 4XW\")
  => 3188
  (distance-between svc \"B30\" \"CF14\")
  => 132945
  ```"
  [svc s1 s2]
  (some-> (pc/distance-between (os-grid-reference svc s1) (os-grid-reference svc s2)) int))

;;
;;
;;

(defn update-from-latest
  "Update service  with the latest NHSPD release."
  [svc]
  (let [{:keys [url] :as release} (dl/latest-release)
        dist (dl/download-url url)
        ch (async/chan 1 (partition-all 1000))]
    (async/thread
      (dl/stream-zip dist ch true))
    (store/update-db (.-ds svc) ch release)))

(defn update-from-csv
  "Update service from a specified csv file. "
  ([svc csv-file]
   (update-from-csv svc csv-file nil))
  ([svc csv-file release]
   (let [ch (async/chan 1 (partition-all 1000))]
     (async/thread
       (dl/stream-csv csv-file ch))
     (store/update-db (.-ds svc) ch release))))

;;
;;
;;

(defn create-latest
  "Create an NHSPD index 'f' using the configuration specified."
  ([f]
   (create-latest f nil))
  ([f config]
   (let [{:keys [url] :as release} (dl/latest-release)
         dist (dl/download-url url)
         ch (async/chan 1 (partition-all 1000))]
     (async/thread
       (dl/stream-zip dist ch true))
     (store/create-db f ch release config))))

(defn create-from-csv
  [f csv-file release config]
  (let [ch (async/chan 1 (partition-all 1000))]
    (async/thread
      (dl/stream-csv csv-file ch true))
    (store/create-db f ch release config)))

(comment
  (dl/latest-release)
  (create-from-csv "nhspd.db" "nhg25may.csv" (dl/latest-release) {:profile :core}))