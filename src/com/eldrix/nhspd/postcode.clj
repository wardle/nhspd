(ns com.eldrix.nhspd.postcode
  "UK postcode normalization and formatting."
  (:require
    [clojure.string :as str]))

(defn ^String normalize
  "Normalizes a postcode into uppercase 8-characters with left-aligned outward
  code and right-aligned inward code returning the original if normalization
  not possible.
  This is the PCD2 standard formatting."
  [pc]
  (when pc
    (let [pc' (str/replace pc #"\s+" "")
          n (count pc')]
      (if-not (>= n 5)
        pc
        (str/upper-case (format "%-4s %3s" (subs pc' 0 (- n 3)) (subs pc' (- n 3))))))))

(defn ^String egif
  "Normalizes a postcode into uppercase with outward code and inward codes
  separated by a single space.
  This is the PCDS standard formatting."
  [pc]
  (when pc (str/replace (normalize pc) #"\s+" " ")))

(defn distance-between
  "Calculates crude distance between two postcodes, determined by the square
  root of the sum of the square of the difference in grid coordinates
  (Pythagoras), result in metres.
  Parameters:
  - pc1d - first postcode NHSPD data (map)
  - pc2d - second postcode NHSPD data (map)"
  [pcd1 pcd2]
  (let [n1 (get pcd1 "OSNRTH1M")
        n2 (get pcd2 "OSNRTH1M")
        e1 (get pcd1 "OSEAST1M")
        e2 (get pcd2 "OSEAST1M")]
    (when (every? number? [n1 n2 e1 e2])
      (let [nd (- n1 n2)
            ed (- e1 e2)]
        (Math/sqrt (+ (* nd nd) (* ed ed)))))))
