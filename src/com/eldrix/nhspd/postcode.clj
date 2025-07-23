(ns com.eldrix.nhspd.postcode
  "UK postcode normalization and formatting."
  (:require
    [clojure.string :as str]))

(defn pcd2
  "Normalizes a postcode into uppercase 8-characters with left-aligned outward
  code and right-aligned inward code returning the original if normalization
  not possible.
  This is the PCD2 standard formatting."
  ^String [s]
  (when-not (str/blank? s)
    (let [s' (str/replace s #"\s+" "")
          n (count s')]
      (if (< n 5)                                           ;; shortest postal code is 5 characters
        s
        (str/upper-case (format "%-4s %3s" (subs s' 0 (- n 3)) (subs s' (- n 3))))))))

(defn ^:deprecated normalize
  "Normalise a postcode to the PCD2 standard."
  [s] (pcd2 s))

(defn egif
  "Normalizes a postcode into uppercase with outward code and inward codes
  separated by a single space.
  This is the PCDS standard formatting."
  ^String [s]
  (when-not (str/blank? s) (str/replace (pcd2 s) #"\s+" " ")))

(defn distance-between
  "Calculates crude distance between two postcodes, determined by the square
  root of the sum of the square of the difference in grid coordinates
  (Pythagoras), result in metres.
  Parameters:
  - pc1d - first postcode NHSPD data (map)
  - pc2d - second postcode NHSPD data (map)"
  [pcd1 pcd2]
  (let [n1 (or (:OSNRTH1M pcd1) (get pcd1 "OSNRTH1M"))
        n2 (or (:OSNRTH1M pcd2) (get pcd2 "OSNRTH1M"))
        e1 (or (:OSEAST1M pcd1) (get pcd1 "OSEAST1M"))
        e2 (or (:OSEAST1M pcd2) (get pcd2 "OSEAST1M"))]
    (when (every? number? [n1 n2 e1 e2])
      (let [nd (- n1 n2)
            ed (- e1 e2)]
        (Math/sqrt (+ (* nd nd) (* ed ed)))))))
