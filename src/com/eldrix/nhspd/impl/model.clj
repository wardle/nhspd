(ns com.eldrix.nhspd.impl.model
  (:require
    [clojure.set :as set]
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
   {:name :OSLAUA, :description "Local authority district (LAD) / unitary authority (UA) / metropolitan district (MD) / London borough (LB) / council area(CA) / district council area (DCA)"}
   {:name :OSWARD, :description "(Electoral) ward / division"}
   {:name :USERTYPE, :type :integer, :description "Postcode user type"}
   {:name :OSGRDIND, :type :integer :description "Grid reference positional quality indicator"}
   {:name :CTRY, :description "Country"}
   {:name :OSHLTHAU, :description "Former Strategic Health Authority (SHA)/ Local Health Board (LHB)/ Health Board (HB)/ Health Authority (HA)/ Health & Social Care Board (HSCB)"}
   {:name :RGN, :description "Region"}
   {:name :OLDHA, :description "Pre-2002 Health Authority"}
   {:name :NHSER, :description "NHS England (Region) (NHSER)"}
   {:name :CCG, :description "Sub ICB Location (LOC)/ Local Health Board (LHB)/ Community Health Partnership (CHP)/ Local Commissioning Group (LCG)/ Primary Healthcare Directorate (PHD)"}
   {:name :PSED, :description "Content removed"}
   {:name :CENED, :description "Content removed"}
   {:name :EDIND, :description "Content removed"}
   {:name :WARD98, :description "Content removed"}
   {:name :OA01, :description "2001 Census Output Area (OA)"}
   {:name :NHSRLO, :description "NHS England (Region, Local Office) (NHSRLO)"}
   {:name :HRO, :description "Former Pan SHA"}
   {:name :LSOA01, :description "2001 Lower Layer Super Output Area (LSOA)/ Super Output Area (SOA)/ Data Zone (DZ)"}
   {:name :UR01IND, :description "Content removed"}
   {:name :MSOA01, :description "Content removed"}
   {:name :CANNET, :description "Former Cancer Network"}
   {:name :SCN, :description "Strategic Clinical Network (NHS SCN)"}
   {:name :OSHAPREV, :description "‘First wave’ Strategic Health Authority (SHA)/ Health Board (HB)/ Health Authority (HA)/ Health & Social Services Board (HSSB)"}
   {:name :OLDPCT, :description "‘First wave’ Primary Care Trust (PCT)/ Local Health Board (LHB)/ Care Trust (CT)"}
   {:name :OLDHRO, :description "‘Old’ IT Cluster (ITC)"}
   {:name :PCON, :description "Westminster parliamentary constituency"}
   {:name :CANREG, :description "Cancer registry"}
   {:name :PCT, :description "‘Second wave’ Primary Care Trust (PCT)/ Care Trust/ Care Trust Plus (CT)/ Local Health Board (LHB)/ Community Health Partnership (CHP)/ Local Commissioning Group (LCG)/ Primary Healthcare Directorate (PHD)"}
   {:name :OSEAST1M, :type :integer, :description "National grid reference - Easting"}
   {:name :OSNRTH1M, :type :integer, :description "National grid reference - Northing"}
   {:name :OA11, :description "2011 Census Output Area (OA)/ Small Area (SA)"}
   {:name :LSOA11, :description "2011 Census Lower Layer Super Output Area (LSOA)/ Super Output Area (SOA)/ Data Zone (DZ)"}
   {:name :MSOA11, :description "2011 Census Middle Layer Super Output Area (MSOA)/ Intermediate Zone (IZ)"}
   {:name :CALNCV, :description "Cancer Alliance (CAL)"}
   {:name :ICB, :description "Integrated Care Board (ICB)"}
   {:name :SMHPC_AED, :description "SMHPC (Adult Eating Disorder services)"}
   {:name :SMHPC_AS, :description "SMHPC (Adult Low and Medium Secure services)"}
   {:name :SMHPC_CT4, :description "SMHPC (Child and Young Persons Mental Health Services)"}
   {:name :OA21, :description "2021 Census Output Area (OA)/ Data Zone (DZ)"}
   {:name :LSOA21 :description "2021 Census Lower Layer Super Output Area (LSOA)/Super Data Zone (SDZ)"}
   {:name :MSOA21, :description "2021 Census Middle Layer Super Output Area (MSOA)"}])

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
  "An ordered sequence of all fields."
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
    (filterv (set/union core-fields (set cols)) all-fields)))

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

(s/def ::profile (s/nilable #{:core :active :current :all}))
(s/def ::col (set all-fields))
(s/def ::cols (s/nilable (s/coll-of ::col)))
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
  ([{:keys [cols profile]}]
   (let [ks (or (custom-profile cols) (get profiles (or profile :current)))
         kset (set ks)]
     (when (seq ks)
       {:fields (filterv (comp kset :name) nhspd-fields)
        :parse  (fn [row] (mget row (mapv field->idx ks)))}))))

(comment
  (mapv :name nhspd-fields)
  (nhspd {:profile :core})
  (let [{:keys [headings parse]} (nhspd {:profile :core})]
    (= headings
       (parse (mapv :name nhspd-fields)))))