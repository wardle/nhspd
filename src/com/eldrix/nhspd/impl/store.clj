(ns com.eldrix.nhspd.impl.store
  (:require
    [clojure.core.async :as async]
    [clojure.data.csv :as csv]
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [com.eldrix.nhspd.impl.model :as model]
    [com.eldrix.nhspd.postcode :as pc]
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs])
  (:import (java.io InputStreamReader)
           (java.time LocalDate LocalDateTime)
           (java.time.format DateTimeFormatter)
           (java.util Locale)
           (javax.sql DataSource)))

(set! *warn-on-reflection* true)

(s/def ::fields ::model/fields)

(def version 1)

(defn set-user-version!
  [conn v]
  (jdbc/execute-one! conn [(str "pragma user_version = " v)]))

(defn user-version
  [conn]
  (:user_version (jdbc/execute-one! conn ["pragma user_version"])))

(defn check-version
  [conn]
  (let [actual (user-version conn)]
    (when (not= version actual)
      (throw (ex-info "incompatible database version" {:expected version, :actual actual})))))

(defn create-tables-sql
  "Return SQL statements to create the nhspd tables."
  [fields]
  ["CREATE TABLE IF NOT EXISTS MANIFEST (id integer primary key, date_time integer not null, release_date integer not null, url text)"
   (str "CREATE TABLE IF NOT EXISTS NHSPD ("
        (str/join ","
                  (map (fn [{nm :name t :type pk :pk nocase :nocase}]
                         (str/join " " (cond-> [(name nm) (if t (name t) "text")]
                                         pk (conj "primary key")
                                         nocase (conj "collate nocase")))) fields)) ")")])

(defn insert-nhspd-sql
  "Return a SQL statement to insert data into the nhspd table."
  [fields]
  (let [field-names (map (comp name :name) fields)]
    (str "INSERT INTO NHSPD (" (str/join "," field-names) ") VALUES ("
         (str/join "," (repeat (count field-names) "?")) ")")))

(defn upsert-nhspd-sql
  [fields]
  (let [field-names (map (comp name :name) fields)]
    (str "INSERT INTO NHSPD (" (str/join "," field-names) ") VALUES ("
         (str/join "," (repeat (count field-names) "?")) ")"
         " ON CONFLICT(PCD2) DO UPDATE SET "
         (str/join "," (map #(str % "=excluded." %) field-names)))))

(defn create-indexes-sql
  [fields]
  (->> fields
       (filter :index)
       (mapv (fn [{n :name}]
               (let [n# (name n)]
                 (str "create index " n# "_idx on nhspd(" n# ")"))))))

(defn drop-indexes-sql
  [fields]
  (->> fields
       (filter :index)
       (mapv (fn [{n :name}]
               (let [n# (name n)]
                 (str "drop index " n#))))))

(defn insert-manifest
  "Records a new row into the store manifest. NOP if 'release' is nil."
  [conn {:keys [date url] :as release}]
  (when release
    (jdbc/execute-one!
      conn
      ["insert into manifest (date_time, release_date, url) values (unixepoch(?),unixepoch(?),?) returning id"
       (LocalDateTime/now) date url]
      {:builder-fn rs/as-unqualified-maps})))

(def dtf (DateTimeFormatter/ofPattern "uuuu-MM-dd HH:mm:ss" Locale/ENGLISH))

(defn parse-local-date-time [s]
  (some-> s (LocalDateTime/parse dtf)))
(defn parse-local-date [s]
  (some-> s (LocalDate/parse dtf)))

(defn manifests
  [conn]
  (->> (jdbc/execute!
         conn
         ["select id, datetime(date_time,'unixepoch') as date_time,
            datetime(release_date, 'unixepoch') as release_date, url
           from manifest
           order by date_time desc"]
         {:builder-fn rs/as-unqualified-maps})
       (map (fn [m]
              (-> m
                  (update :date_time parse-local-date-time)
                  (update :release_date parse-local-date))))))

(defn count-postcodes
  [conn]
  (:COUNT (jdbc/execute-one! conn ["SELECT COUNT(*) AS COUNT FROM NHSPD"])))

(defn model
  [config]
  (when-not (s/valid? ::model/params config)
    (throw (ex-info "invalid parameters" (s/explain-data ::model/params config))))
  (let [{:keys [fields] :as m} (model/nhspd config)]
    (assoc m
      :config config
      :create-tables (create-tables-sql fields)
      :create-indexes (create-indexes-sql fields)
      :insert (insert-nhspd-sql fields)
      :upsert (upsert-nhspd-sql fields)
      :drop-indexes (drop-indexes-sql fields))))

(defn column-names
  "Return a vector of column names (as keywords) for the NHSPD table"
  [conn]
  (into [] (comp (map :name) (map keyword)) (jdbc/plan conn ["pragma table_info('nhspd')"])))

(defn execute-stmts
  [conn stmts]
  (doseq [stmt stmts]
    (jdbc/execute-one! conn [stmt])))

(defn write-from-csv
  "Write the NHSPD data in 'f' to the database 'conn' using parse function
  'parse' and SQL statement 'sql'."
  [conn f parse sql]
  (with-open [is (io/input-stream f)]
    (let [batches (->> is
                       (InputStreamReader.)
                       (csv/read-csv)
                       (map parse)
                       (partition-all 5000))]
      (doseq [batch batches]
        (jdbc/with-transaction [txn conn]
          (jdbc/execute-batch! txn sql batch {}))))))

(defn write-from-ch
  ([conn ch parse sql]
   (write-from-ch conn ch parse sql 0))
  ([conn ch parse sql delay]
   (loop []
     (when-let [batch (async/<!! ch)]
       (jdbc/with-transaction [txn conn]
         (jdbc/execute-batch! txn sql (map parse batch) {}))
       (when (pos-int? delay) (^[long] Thread/sleep delay))
       (recur)))))

(defn open-db
  "Open SQLite database. See https://github.com/xerial/sqlite-jdbc/blob/master/USAGE.md
  for URL specification, but essentially dbname should be a path to the SQLite
  database file."
  ^DataSource [dbname]
  (jdbc/get-datasource {:dbtype "sqlite" :dbname (str dbname)}))

(defn create-db
  "Create a database file 'database-file'. Optionally imports data from 'ch' if
  provided; this occurs prior to indexing so is faster than if performed after
  database creation. Returns a DataSource.
  Parameters:
  - dbname  : JDBC 'dbname' - essentially path to sqlite file
  - ch      : clojure.core.async channel with batches of postcode data, can be nil
  - config  : optional configuration map:
               |- :release - :date and :url of release
               |- :cols    - sequence of NHSPD columns
               |- :profile - one of :core :active :current :all"
  ^DataSource [dbname ch {:keys [release _cols _profile] :as config}]
  (let [ds (open-db dbname)
        {:keys [parse create-tables create-indexes insert]} (model config)]
    (execute-stmts ds create-tables)
    (set-user-version! ds version)
    (insert-manifest ds release)
    (execute-stmts ds ["pragma journal_mode = WAL"
                       "pragma synchronous = normal"
                       "pragma journal_size_limit = 6144000"])
    (when ch (write-from-ch ds ch parse insert))
    (execute-stmts ds create-indexes)
    (execute-stmts ds ["pragma journal_mode = DELETE"])
    (jdbc/execute-one! ds ["vacuum"])
    ds))

(defn update-db
  "Update a database in-place from the clojure.core.async channel specified. "
  [ds ch {:keys [release delay]}]
  (let [{:keys [parse upsert]} (model {:cols (column-names ds)})]
    (check-version ds)
    (when ch
      (insert-manifest ds release)
      (write-from-ch ds ch parse upsert delay))
    (jdbc/execute-one! ds ["vacuum"])))

;;
;;
;;

(defn postcode
  "Return NHSPD data for the given postcode. Returns data as map of keyword keys
  to values."
  [conn s]
  (when-let [s' (pc/pcd2 s)]
    (jdbc/execute-one! conn ["select * from nhspd where pcd2=?" s']
                       {:builder-fn rs/as-unqualified-maps})))

(defn fetch-postcode
  "Returns NHSPD data for given postcode with result as map with string keys.
  Generally, [[postcode]] should be preferred."
  [conn s]
  (some-> (postcode conn s) (update-keys name)))

(defn ^:private avg-loc
  "Returns the 'average' location data for a given postcode or prefix. Prefix
  must be two characters or greater."
  [conn s]
  (when (>= (count s) 2)
    (when-let [{:keys [OSNRTH1M OSEAST1M]}
               (jdbc/execute-one! conn
                                  ["select avg(OSNRTH1M) as OSNRTH1M, avg(OSEAST1M) as OSEAST1M
                                         from NHSPD where PCD2 like ?" (str s "%")]
                                  {:builder-fn rs/as-unqualified-maps})]
      {:OSNRTH1M (some-> OSNRTH1M int)
       :OSEAST1M (some-> OSEAST1M int)})))

(defn os-grid-reference
  "Return Ordnance Survey grid reference data for a postcode.
  Parameters:
  - s : either a full postcode, or prefix.
  For a prefix, the average location of all postcodes with that prefix will be
  returned."
  [conn s]
  (if-let [pc (postcode conn s)]
    (select-keys pc [:OSNRTH1M :OSEAST1M])
    (avg-loc conn s)))

(comment
  (def f "nhg25may.csv")
  (def conn (jdbc/get-connection "jdbc:sqlite:nhspd1.db"))
  (def ch (async/chan 1 (partition-all 500)))
  (def model (model/nhspd {:profile :core})))

