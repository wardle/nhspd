(ns com.eldrix.nhspd.impl.model
  (:require [clojure.set :as set]
            [clojure.spec.alpha :as s]))

(s/def ::name keyword?)
(s/def ::description string?)
(s/def ::type #{:integer :string})
(s/def ::field (s/keys :req-un [::name]
                       :opt-un [::description ::type ::pk ::nocase ::index]))
(s/def ::fields (s/coll-of ::field))

(def nhspd-fields
  "See the NHSPD user guide and the NHSPD record specification."
  [{:name :PCD2, :description "Unit postcode – 8 character version" :pk true :nocase true :index true}
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
   {:name :OSGRDIND, :type :integer :description "Grid reference\npositional quality\nindicator"}
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

(def nhspd-fields#
  (map-indexed (fn [idx m] (assoc m :idx idx)) nhspd-fields))

(def field->idx
  "A map of field to index."
  (reduce (fn [acc {:keys [idx name]}]
            (assoc acc name idx))
          {}
          nhspd-fields#))

(def core-fields
  #{:PCD2 :PCDS :USERTYPE :OSGRDIND :OSEAST1M :OSNRTH1M})

(def active-fields
  "All fields except those 'withdrawn'"
  #{:PCD2 :PCDS :DOINTR :DOTERM :OSEAST100M :OSNRTH100M :OSCTY :ODSLAUA :OSLAUA :OSWARD :USERTYPE :OSGRDIND :CTRY
    :OSHLTHAU :RGN :OLDHA :NHSER :CCG :OA01 :NHSRLO :HRO :LSOA01 :CANNET :SCN :OSHAPREV :OLDPCT :OLDHRO :PCON :CANREG
    :PCT :OSEAST1M :OSNRTH1M :OA11 :LSOA11 :MSOA11 :CALNCV :ICB :SMHPC_AED :SMHPC_AS :SMHPC_CT4 :OA21 :LSOA21 :MSOA21})

(def current-fields
  "All fields except those 'withdrawn' or historic."
  #{:PCD2 :PCDS :DOINTR :DOTERM :OSEAST100M :OSNRTH100M :OSCTY :ODSLAUA :OSLAUA :OSWARD :USERTYPE :OSGRDIND :CTRY
    :RGN :NHSER :CCG :OA01 :NHSRLO :LSOA01 :SCN :PCON :CANREG :PCT :OSEAST1M :OSNRTH1M :OA11 :LSOA11 :MSOA11 :CALNCV :ICB :SMHPC_AED :SMHPC_AS :SMHPC_CT4 :OA21 :LSOA21 :MSOA21})

(def all-fields
  (mapv :name nhspd-fields))

(def profiles
  "Vectors of fields by profiles"
  {:core    (filterv core-fields all-fields)
   :active  (filterv active-fields all-fields)
   :current (filterv current-fields all-fields)
   :all     all-fields})

(defn custom-profile
  "Return an ordered sequence of fields with a custom set of 'cols'. This will
  ALWAYS return the core set of fields which are the minimum for operation of
  this library."
  [cols]
  (when (seq cols)
    (filterv (clojure.set/union core-fields (set cols)) all-fields)))

(defn mget
  "Like [[clojure.core/get]] except works for multiple keys returning a vector."
  [v ks]
  (loop [ks ks, ret (transient [])]
    (if-not (seq ks)
      (persistent! ret)
      (recur (rest ks) (conj! ret (get v (first ks)))))))

;;
;;
;;

(s/def ::profile #{:core :active :current :all})
(s/def ::cols (s/every (set all-fields)))
(s/def ::params (s/nilable (s/keys :opt-un [::profile ::cols])))

(s/fdef nhspd
  :args (s/cat :params ::params))

(defn nhspd
  "Return a map of :headings and :parse representing the NHSPD data model. As
  the NHSPD model has changed over the years, different columns have been
  deprecated. It would therefore be unusual to need to 'store' all data and it
  is usually better to use one of the predefined 'profiles'.

  Parameters:
  - :cols    : vector of columns to be 'selected'.
  - :profile : one of :core, :active, :current, :all (default, :current).

  Returns
  - :fields   : sequence of 'selected' fields (:name, :description, :type).
  - :parse    : a function that will return a vector of selected headings from
                NHSPD row data."
  ([]
   (nhspd {:profile :current}))
  ([{:keys [cols profile] :or {profile :current}}]
   (let [ks (or (custom-profile cols) (get profiles profile))
         kset (set ks)]
     (when (seq ks)
       {:fields   (filterv (comp kset :name) nhspd-fields)
        :parse    (fn [row] (mget row (mapv field->idx ks)))}))))

(comment
  (mapv :name nhspd-fields)
  (nhspd {:profile :core})
  (let [{:keys [headings parse]} (nhspd {:profile :core})]
    (= headings
       (parse (mapv :name nhspd-fields)))))