(ns com.eldrix.nhspd.postcode-test
  (:require [clojure.test :refer :all]
            [clojure.spec.gen.alpha :as gen]
            [com.eldrix.nhspd.postcode :as pc]))

(deftest test-postcode-normalization-edge-cases
  (testing "Known edge cases"
    (is (nil? (pc/pcd2 nil)))
    (is (nil? (pc/pcd2 "")))
    (is (= "CF14 4XW" (pc/pcd2 "CF14    4XW")))))

(deftest test-pcd2-robustness
  (testing "PCD2 normalization doesn't crash on generated input"
    (let [test-inputs (gen/sample (gen/string-alphanumeric) 50)]
      (doseq [input test-inputs]
        (testing (str "Input: " (pr-str input))
          (is (or (nil? (pc/pcd2 input))
                  (string? (pc/pcd2 input)))))))))