(ns com.eldrix.nhspd.impl.cli-test
  (:require [clojure.test :refer :all]
            [com.eldrix.nhspd.impl.cli :as cli]))

(def cli-test-cases
  [{:desc "Profile and column mutual exclusion should error"
    :args ["create" "--db" "test.db" "--profile" "core" "--column" "CCG"]
    :error true}
   
   {:desc "Invalid column names should be rejected"
    :args ["create" "--db" "test.db" "-c" "INVALID_COLUMN"]
    :error true}
   
   {:desc "Invalid port should error"
    :args ["serve" "--db" "test.db" "--port" "0"]
    :error true}
   
   {:desc "Valid port should not error"
    :args ["serve" "--db" "test.db" "--port" "8080"]
    :error false}
   
   {:desc "Invalid month in date should error"
    :args ["import" "--db" "test.db" "--release" "2025-13-01"]
    :error true}
   
   {:desc "Invalid date format should error"
    :args ["import" "--db" "test.db" "--release" "not-a-date"]
    :error true}

   {:desc "Invalid date format should error"
    :args ["import" "--db" "test.db" "--release" "20250101"]
    :error true}

   {:desc "Valid date should not error"
    :args ["import" "--db" "test.db" "--release" "2025-01-01"]
    :error false}
   
   {:desc "Valid create command with profile"
    :args ["create" "--db" "test.db" "--profile" "core"]
    :f    (fn [{:keys [cmd options]}]
            (is (= "create" cmd))
            (is (= :core (:profile options))))}
   
   {:desc "Valid serve command with all options"
    :args ["serve" "--db" "test.db" "--port" "9000" "--bind" "localhost"]
    :f    (fn [{:keys [cmd options]}]
            (is (= "serve" cmd))
            (is (= 9000 (:port options)))
            (is (= "localhost" (:bind options))))}
   
   {:desc "Valid import with multiple columns"
    :args ["import" "--db" "test.db" "-c" "CCG,ICB" "-c" "LSOA11"]
    :f    (fn [{:keys [cmd options]}]
            (is (= "import" cmd))
            (is (= [:CCG :ICB :LSOA11] (:column options))))}
   
   {:desc "Status command with JSON format"
    :args ["status" "--db" "test.db" "--format" "json"]
    :f    (fn [{:keys [cmd options]}]
            (is (= "status" cmd))
            (is (= :json (:format options))))}])

(deftest test-cli-parsing
  (testing "CLI argument parsing"
    (doseq [{:keys [desc args error f]} cli-test-cases]
      (testing desc
        (let [{:keys [errors] :as parsed} (cli/parse-args args)]
          (when (some? error)
            (if error (is (seq errors)) (is (empty? errors))))
          (when f (f parsed)))))))