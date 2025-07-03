(ns com.eldrix.nhspd.parse
  "Provides functionality to parse 'NHS Postcode Data'."
  (:require [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [clojure.core.async :as async]
            [clojure.string :as str]
            [next.jdbc :as jdbc])
  (:import (java.io InputStreamReader)))

(def nhspd-fields
  "See the NHSPD user guide and the NHSPD record specification."
  [{:name :PCD2, :description "Unit postcode – 8 character version"}
   {:name :PCDS, :description "Unit postcode -variable length (e-Gif) version"}
   {:name :DOINTR, :description "Date of introduction"}
   {:name :DOTERM, :description "Date of termination"}
   {:name :OSEAST100M, :type :integer, :description "National grid reference -Easting"}
   {:name :OSNRTH100M, :type :integer, :description "National grid reference - Northing"}
   {:name :OSCTY, :description "County"}
   {:name :ODSLAUA, :description "Local Authority Organisation"}
   {:name :OSLAUA, :description "Local authority\ndistrict\n(LAD)/unitary\nauthority (UA)/\nmetropolitan\ndistrict (MD)/\nLondon borough\n(LB)/ council area\n(CA)/district\ncouncil area\n(DCA)"}
   {:name :OSWARD, :description "(Electoral)\nward/division"}
   {:name :USERTYPE, :type :integer, :description "Postcode user type"}
   {:name :OSGRDIND, :description "Grid reference\npositional quality\nindicator"}
   {:name :CTRY, :description "Country"}
   {:name :OSHLTHAU, :description "Former Strategic\nHealth Authority\n(SHA)/ Local\nHealth Board\n(LHB)/ Health\nBoard (HB)/\nHealth Authority\n(HA)/ Health &\nSocial Care Board\n(HSCB)"}
   {:name :RGN, :description "Region"}
   {:name :OLDHA, :description "Pre-2002 Health\nAuthority"}
   {:name :NHSER, :description "NHS England\n(Region) (NHSER)"}
   {:name :CCG, :description "Sub ICB Location\n(LOC)/ Local\nHealth Board\n(LHB)/\nCommunity\nHealth\nPartnership\n(CHP)/ Local\nCommissioning\nGroup (LCG)/\nPrimary\nHealthcare\nDirectorate (PHD)"}
   {:name :PSED, :description "Content removed"}
   {:name :CENED, :description "Content removed"}
   {:name :EDIND, :description "Content removed"}
   {:name :WARD98, :description "Content removed"}
   {:name :OA01, :description "2001 Census\nOutput Area (OA)"}
   {:name :NHSRLO, :description "NHS England\n(Region, Local\nOffice) (NHSRLO)"}
   {:name :HRO, :description "Former Pan SHA"}
   {:name :LSOA01, :description "2001 Lower Layer\nSuper Output\nArea (LSOA)/\nSuper Output\nArea (SOA)/ Data\nZone (DZ)"}
   {:name :UR01IND, :description "Content removed"}
   {:name :MSOA01, :description "Content removed"}
   {:name :CANNET, :description "Former Cancer\nNetwork"}
   {:name :SCN, :description "Strategic Clinical\nNetwork (NHS\nSCN)"}
   {:name :OSHAPREV, :description "‘First wave’\nStrategic Health\nAuthority (SHA)/\nHealth Board\n(HB)/ Health\nAuthority (HA)/\nHealth & Social\nServices Board\n(HSSB)"}
   {:name :OLDPCT, :description "‘First wave’\nPrimary Care Trust\n(PCT)/ Local\nHealth Board\n(LHB)/ Care Trust\n(CT)"}
   {:name :OLDHRO, :description "‘Old’ IT Cluster\n(ITC)"}
   {:name :PCON, :description "Westminster\nparliamentary\nconstituency"}
   {:name :CANREG, :description "Cancer registry"}
   {:name :PCT, :description "‘Second wave’\nPrimary Care Trust\n(PCT)/ Care Trust/\nCare Trust Plus\n(CT)/ Local Health\nBoard (LHB)/\nCommunity\nHealth\nPartnership\n(CHP)/ Local\nCommissioning\nGroup (LCG)/\nPrimary\nHealthcare\nDirectorate (PHD)"}
   {:name :OSEAST1M, :type :integer, :description "National grid\nreference -\nEasting"}
   {:name :OSNRTH1M, :type :integer, :description "National grid\nreference -\nNorthing"}
   {:name :OA11, :description "2011 Census\nOutput Area (OA)/\nSmall Area (SA)"}
   {:name :LSOA11, :description "2011 Census\nLower Layer Super\nOutput Area\n(LSOA)/ Super\nOutput Area\n(SOA)/ Data Zone\n(DZ)"}
   {:name :MSOA11, :description "2011 Census\nMiddle Layer\nSuper Output\nArea (MSOA)/\nIntermediate\nZone (IZ)"}
   {:name :CALNCV, :description "Cancer Alliance\n(CAL)"}
   {:name :ICB, :description "Integrated Care\nBoard (ICB)"}
   {:name :SMHPC_AED, :description "SMHPC (Adult\nEating Disorder\nservices)"}
   {:name :SMHPC_AS, :description "SMHPC (Adult\nLow and Medium\nSecure services)"}
   {:name :SMHPC_CT4, :description "SMHPC (Child and\nYoung Persons\nMental Health\nServices)"}
   {:name :OA21, :description "2021 Census\nOutput Area (OA)/\nData Zone (DZ)"}
   {:name :LSOA21 :description "2021 Census\nLower Layer Super\nOutput Area\n(LSOA)/Super\nData Zone (SDZ)"}
   {:name :MSOA21, :description "2021 Census\nMiddle Layer\nSuper Output\nArea (MSOA)"}])

(def nhspd-field-names
  (mapv (comp name :name) nhspd-fields))

(def create-nhspd-sql
  "Return a SQL statement to create the nhspd table."
  (str "CREATE TABLE IF NOT EXISTS NHSPD ("
       (str/join ","
                 (map (fn [{nm :name t :type}]
                        (str (name nm) " " (if t (name t) "text"))) nhspd-fields)) ")"))

(def insert-nhspd-sql
  "Return a SQL statement to insert data into the nhspd table."
  (str "INSERT INTO NHSPD (" (str/join "," nhspd-field-names) ") VALUES ("
       (str/join "," (repeat (count nhspd-field-names) "?")) ")"))

(defn- ^:deprecated parse-coords
  [{:strs [OSNRTH1M OSEAST1M OSNRTH100M OSEAST100M] :as pc}]
  (-> pc
      (assoc "OSNRTH1M" (when-not (str/blank? OSNRTH1M) (Integer/parseInt OSNRTH1M)))
      (assoc "OSEAST1M" (when-not (str/blank? OSEAST1M) (Integer/parseInt OSEAST1M)))
      (assoc "OSNRTH100M" (when-not (str/blank? OSNRTH100M) (Integer/parseInt OSNRTH100M)))
      (assoc "OSEAST100M" (when-not (str/blank? OSEAST100M) (Integer/parseInt OSEAST100M)))))

(defn ^:deprecated import-postcodes
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

(defn stream-postcodes
  "Blocking; stream postcode data to the channel specified as a vector of 
  fields. It would be usual to run this in a background thread such as by 
  using `async/thread`.
  Parameters:
    - in     : An argument that can be coerced into an input stream (see io/input-stream)
    - ch     : The channel to use
    - close? : If the channel should be closed when done."
  ([in ch]
   (stream-postcodes in ch true))
  ([in ch close?]
   (with-open [is (io/input-stream in)]
     (async/<!! (async/onto-chan!! ch (->> is (InputStreamReader.) (csv/read-csv)))))
   (when close? (async/close! ch))))

(defn write-nhspd
  [conn f]
  (with-open [is (io/input-stream f)]
    (let [batches (->> is
                       (InputStreamReader.)
                       (csv/read-csv)
                       (partition-all 500))]
      (doseq [batch batches]
        (jdbc/with-transaction [txn conn]
          (jdbc/execute-batch! conn insert-nhspd-sql batch {}))))))


(comment
  ;; this is the Feb 2020 release file (928mb) already downloaded and unzipped.
  (def nhspd "/Users/mark/Downloads/NHSPD_UK_FULL/Full File Package - ODS/Data/nhg24feb.csv")
  (def ch (async/chan 1 (partition-all 500)))
  (async/thread (stream-postcodes nhspd ch))
  (async/<!! ch)
  (def conn (jdbc/get-connection "jdbc:sqlite:nhspd2.db"))
  (jdbc/execute! conn [create-nhspd-sql])
  (write-nhspd conn nhspd))
