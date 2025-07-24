(ns com.eldrix.nhspd.api-test
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.test :refer :all]
            [com.eldrix.nhspd.api :as api]
            [com.eldrix.nhspd.impl.model :as model])
  (:import [java.io File]))

(defn with-test-db
  "Execute function 'f' with temporary test database"
  ([f] (with-test-db {} f))
  ([config f]
   (let [temp-db (File/createTempFile "test-nhspd" ".db")]
     (try
       (let [svc (api/create-from-files temp-db [(io/resource "nhspd-test-data.csv")] config)]
         (f svc))
       (finally (.delete temp-db))))))

(deftest test-distance-calculation
  (testing "Distance between known postcodes"
    (with-test-db
      (fn [svc]
        (let [distance (api/distance-between svc "CF14 4XW" "CF14 2TL")]
          (is (number? distance))
          (is (< 2900 distance 3000)))))))

(deftest test-coordinate-conversion
  (with-test-db
    (fn [svc]
      (let [pc-data (api/os-grid-reference svc "CF14 4XW")
            {:keys [WGS84LAT WGS84LNG]} (api/with-wgs84 pc-data)]
        ;; Cardiff should be approximately 51.48°N, -3.18°W
        (is (< 51.4 WGS84LAT 51.6))
        (is (< -3.3 WGS84LNG -3.0))))))

(deftest test-generated-column-combinations
  (testing "Generated column combinations work correctly"
    (let [column-samples (gen/sample (s/gen ::model/cols) 10)]
      (doseq [cols column-samples]
        (testing (str "Column combination: " cols)
          (with-test-db
            {:cols cols}
            (fn [svc]
              (let [{:keys [PCD2 OSEAST1M OSNRTH1M] :as pc} (api/postcode svc "CF14 4XW")
                    expected-cols (into model/core-fields cols)
                    actual-cols (set (keys pc))]
                (is (= "CF14 4XW" PCD2))
                (is (number? OSEAST1M))
                (is (number? OSNRTH1M))
                ;; Check that all requested columns are present
                (is (clojure.set/subset? expected-cols actual-cols)
                    (str "Missing columns: " (clojure.set/difference expected-cols actual-cols)))))))))))

(deftest test-all-profiles
  (testing "All predefined profiles work"
    (doseq [profile [:core :active :current :all]]
      (with-test-db
        {:profile profile}
        (fn [svc]
          (let [{:keys [PCD2]} (api/postcode svc "CF14 4XW")]
            (is (= "CF14 4XW" PCD2))))))))

(deftest ^:live test-real-data-integration
  (testing "Integration with real NHSPD data"
    (let [temp-db (File/createTempFile "nhspd" ".db")]
      (try
        (api/create-latest (.getPath temp-db) {:profile :all})
        (with-open [svc (api/open (.getPath temp-db))]
          (let [{:keys [n] :as status} (api/status svc)
                {:keys [PCD2]} (api/postcode svc "CF14 4XW")]
            (pp/pprint status)
            (is (> n 2000000) "Should be over 2 million postcodes")
            (is (= "CF14 4XW" PCD2))))
        (finally (.delete temp-db))))))