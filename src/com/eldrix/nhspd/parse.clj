(ns com.eldrix.nhspd.parse
  "Provides functionality to parse 'NHS Postcode Data'."
  (:require [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [clojure.core.async :as async]
            [clojure.string :as str])
  (:import (java.io InputStreamReader)))

(def nhspd-field-names
  ["PCD2" "PCDS" "DOINTR" "DOTERM" "OSEAST100M"
   "OSNRTH100M" "OSCTY" "ODSLAUA" "OSLAUA" "OSWARD"
   "USERTYPE" "OSGRDIND" "CTRY" "OSHLTHAU" "RGN"
   "OLDHA" "NHSER" "CCG" "PSED" "CENED"
   "EDIND" "WARD98" "OA01" "NHSRLO" "HRO"
   "LSOA01" "UR01IND" "MSOA01" "CANNET" "SCN"
   "OSHAPREV" "OLDPCT" "OLDHRO" "PCON" "CANREG"
   "PCT" "OSEAST1M" "OSNRTH1M" "OA11" "LSOA11"
   "MSOA11" "CALNCV" "STP"])

(defn- parse-coords
  [{:strs [OSNRTH1M OSEAST1M OSNRTH100M OSEAST100M] :as pc}]
  (-> pc
      (assoc "OSNRTH1M" (when-not (str/blank? OSNRTH1M) (Integer/parseInt OSNRTH1M)))
      (assoc "OSEAST1M" (when-not (str/blank? OSEAST1M) (Integer/parseInt OSEAST1M)))
      (assoc "OSNRTH100M" (when-not (str/blank? OSNRTH100M) (Integer/parseInt OSNRTH100M)))
      (assoc "OSEAST100M" (when-not (str/blank? OSEAST100M) (Integer/parseInt OSEAST100M)))))

(defn import-postcodes
  "Import postcodes to the channel specified.
  Each item is returned formatted as a map with the NHSPD data.
  Ordnance survey grid coordinates are parsed as integers, if present.
  Parameters:
    - in     : An argument that can be coerced into an input stream (see io/input-stream)
    - ch     : The channel to use
    - close? : If the channel should be closed when done."
  ([in ch] (import-postcodes in ch true))
  ([in ch close?]
   (with-open [is (io/input-stream in)]
     (->> is
          (InputStreamReader.)
          (csv/read-csv)
          (map #(zipmap nhspd-field-names %))
          (map parse-coords)
          (run! #(async/>!! ch %))))
   (when close? (async/close! ch))))

(comment
  ;; this is the Feb 2020 release file (928mb) already downloaded and unzipped.
  (def nhspd "/Users/mark/Downloads/NHSPD_FEB_2020_UK_FULL/Data/nhg20feb.csv")
  (def ch (async/chan))
  (async/thread (import-postcodes nhspd ch))
  (async/<!! ch))
