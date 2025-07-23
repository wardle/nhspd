(ns com.eldrix.nhspd.api
  "The NHS Postcode Directory (NHSPD) relates both current and terminated postcodes in the United
Kingdom to a range of current statutory administrative, electoral, health and other area geographies.
It also links postcodes to pre-2002 health areas, 1991 Census enumeration districts (for England and
Wales) and both 2001 Census and 2011 Census Output Areas and Super Output Areas. It helps support
the production of area-based statistics from postcoded data. The NHSPD is produced by ONS
Geography, who provide geographic support to the Office for National Statistics (ONS) and geographic
services used by other organisations. They issue the NHSPD quarterly."
  (:require
    [clojure.core.async :as async]
    [com.eldrix.nhspd.impl.coords :as coords]
    [com.eldrix.nhspd.impl.download :as dl]
    [com.eldrix.nhspd.impl.model :as model]
    [com.eldrix.nhspd.impl.store :as store]
    [com.eldrix.nhspd.postcode :as pc]
    [next.jdbc :as jdbc])
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
  to values.
  For example,
  ```
  (nhspd/postcode nhspd \"CF14 4XW\")
  =>
  {:PCD2 \"CF14 4XW\", :PCDS \"CF14 4XW\", :USERTYPE 0, :OSGRDIND 1, :OSEAST1M 317518, :OSNRTH1M 179363}\n
  ```"
  [svc s]
  (store/postcode (.-ds svc) s))

(defn fetch-postcode
  "Returns NHSPD data for given postcode with result as map, but unlike
  [[postcode]], returns data with *string* keys.
  For example,
  ```
  (nhspd/fetch-postcode nhspd \"CF14 4XW\")
  =>
  {\"PCD2\" \"CF14 4XW\", \"PCDS\" \"CF14 4XW\", \"USERTYPE\" 0, \"OSGRDIND\" 1, \"OSEAST1M\" 317518, \"OSNRTH1M\" 179363}
  ```"
  [svc s]
  (store/fetch-postcode (.-ds svc) s))

(defn os-grid-reference
  "Return Ordnance Survey grid reference data for a postcode.
  Parameters:
  - s : either a full postcode, or prefix.
  For a prefix, the average location of all postcodes with that prefix will be
  returned.
  For example,
  ```
  (nhspd/os-grid-reference nhspd \"CF14 4XW\")
  =>
  {:OSNRTH1M 179363, :OSEAST1M 317518}

  (nhspd/os-grid-reference nhspd \"CF14\")
  =>
  {:OSNRTH1M 180611, :OSEAST1M 316282}
  ```"
  [svc s]
  (store/os-grid-reference (.-ds svc) s))

(defn with-wgs84
  "Add :WGS84LAT and :WGS84LNG to the NHSPD data based on the Ordnance Survey
  easting/northing coordinates.
  For example,
  ```
  (nhspd/with-wgs84 (nhspd/os-grid-reference nhspd \"CF14\"))
  =>
  {:OSNRTH1M 180611, :OSEAST1M 316282, :WGS84LAT 52.73734104485671, :WGS84LNG -3.240100747759483}\n
  ```"
  [pc]
  (coords/with-wgs84 pc))

(defn distance-between
  "Return the distance in metres between two UK postal codes. This works for
  full postcodes and prefixes.
  For example,
  ```
  (distance-between svc \"CF14 2HB\" \"CF14 4XW\")
  => 3188
  (distance-between svc \"B30\" \"CF14\")
  => 132945
  ```"
  [svc s1 s2]
  (some-> (pc/distance-between (os-grid-reference svc s1) (os-grid-reference svc s2)) int))

(defn status
  "Return status information about the NHSPD service including manifests,
  columns, and version information."
  [svc]
  (with-open [conn (jdbc/get-connection (.-ds svc))]
    (let [cols (set (store/column-names conn))
          columns (filterv #(cols (:name %)) model/nhspd-fields)]
      {:n         (store/count-postcodes conn)
       :manifests (store/manifests conn)
       :columns   columns
       :version   (store/user-version conn)})))

;;
;;
;;

(defn update-from-latest
  "Update service with the latest NHSPD release."
  ([svc]
   (update-from-latest svc {}))
  ([svc {:keys [delay]}]
   (let [{:keys [url] :as release} (dl/latest-release)
         zipfile (dl/download-url url)
         ch (async/chan 1 (partition-all 1000))]
     (async/thread
       (dl/stream-zip zipfile ch true))
     (store/update-db (.-ds svc) ch {:release release :delay delay})
     (dl/delete zipfile))))

(defn update-from-files
  "Import 'files' into existing service."
  [svc files {:keys [release delay]}]
  (let [ch (async/chan 1 (partition-all 1000))
        release (when release {:date release})]
    (async/thread (dl/stream-files ch files true))
    (store/update-db (.-ds svc) ch {:release release :delay delay})))

;;
;;
;;

(defn create-latest
  "Create an NHSPD index 'f' using the configuration specified."
  ([f]
   (create-latest f nil))
  ([f config]
   (let [{:keys [url] :as release} (dl/latest-release)
         zipfile (dl/download-url url)
         ch (async/chan 1 (partition-all 1000))]
     (async/thread
       (dl/stream-zip zipfile ch true))
     (store/create-db f ch (assoc config :release release))
     (dl/delete zipfile))))

(defn create-from-files
  "Create database 'f' by importing 'files'. Returns created NHSPD 'service'."
  [f files {:keys [profile cols release]}]
  (let [ch (async/chan 1 (partition-all 1000))
        release (when release {:date release})]
    (async/thread (dl/stream-files ch files true))
    (->NHSPD (store/create-db f ch {:release release :profile profile :cols cols}))))

(comment
  (dl/latest-release)
  (create-from-files "nhspd.db" ["nhg25may.csv"] {:release (dl/latest-release), :profile :core}))