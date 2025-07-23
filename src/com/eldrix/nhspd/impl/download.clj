(ns com.eldrix.nhspd.impl.download
  (:require [clojure.core.async :as async]
            [clojure.data.csv :as csv]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.tools.logging.readable :as log]
            [hato.client :as hc])
  (:import (java.io File InputStreamReader)
           (java.nio.file Files)
           (java.time LocalDate)
           (java.util.zip ZipFile)))

(defn download-url
  "Download a file from the URL specified.
  Parameters:
  - url : the URL to fetch
  - out   : (optional) the java.io.File/Writer/OutputStream to which to write.
  If no `out` specified, a temporary file will be created and returned."
  ([url]
   (download-url url (File/createTempFile "nhspd" ".zip")))
  ([url out]
   (io/copy (:body (hc/get url {:as :stream :http-client {:redirect-policy :always}})) out)
   out))

(defn delete
  [f]
  (.delete (io/file f)))

(defn latest-release
  "Returns information about the latest NHSPD release.
   At the moment, there is no available API for this, so we fake by using a
   local embedded datafile that can be updated with the latest release date
  and URL. The NHSPD is currently released quarterly."
  []
  (let [releases (map #(assoc % :date (LocalDate/parse (:date %)))
                      (edn/read-string (slurp (io/resource "nhspd.edn"))))]
    (first (reverse (sort-by :date releases)))))

(defn possibly-outdated?
  "Returns 'true' if latest release, or the specified release, is outdated. This
  is defined as more than 3 months old."
  ([]
   (possibly-outdated? (latest-release)))
  ([release]
   (let [{:keys [date]} release]
     (if date (.isAfter (LocalDate/now) (.plusMonths date 3))
              (throw (ex-info "no latest release!" release))))))

(defn stream-csv
  "Blocking; stream postcode data to the channel specified as a vector of
  fields. It would be usual to run this in a background thread such as by
  using `async/thread`.
  Parameters:
    - in     : An argument that can be coerced into an input stream (see io/input-stream)
    - ch     : The channel to use
    - close? : If the channel should be closed when done."
  ([in ch]
   (stream-csv in ch true))
  ([in ch close?]
   (with-open [is (io/input-stream in)]
     (async/<!! (async/onto-chan!! ch (->> is (InputStreamReader.) (csv/read-csv)))))
   (when close? (async/close! ch))))

(defn stream-zip
  "Blocking; stream postcode data from the zip file to the channel.
  Parameters:
  - f      : filename or java.io.File for the NHSPD zip file.
  - ch     : clojure.core.async channel
  - close? : optional; whether to close the channel when done (default, true)."
  ([f ch]
   (stream-zip f ch true))
  ([f ch close?]
   (with-open [zf (ZipFile. f)]
     (if-let [all-pcodes-file (first (filter #(re-matches #"Data/nhg.*\.csv" (.getName %)) (enumeration-seq (.entries zf))))]
       (stream-csv (.getInputStream zf all-pcodes-file) ch close?)
       (throw (ex-info "Unable to find postcode file in archive. We looked for a file 'nhg*.csv'. Has archive format changed?" {}))))))

(defn stream-file
  "Blocking; stream file 'f' to clojure.core.async channel 'ch'. Handles CSV and
  zip files"
  ([f ch]
   (stream-file f ch true))
  ([f ch close?]
   (let [content-type (Files/probeContentType (.toPath (io/file f)))]
     (log/debug "streaming file" {:f f :content-type content-type})
     (case content-type
       "application/zip" (stream-zip f ch close?)
       "text/csv" (stream-csv f ch close?)
       (throw (ex-info "File not recognised" {:f f :content-type content-type}))))))

(defn stream-files
  "Stream the contents of each file in 'files' to the channel 'ch'."
  [ch files close?]
  (loop [files files]
    (if-let [f (first files)]
      (do
        (log/debug "processing:" f)
        (stream-file f ch false)
        (recur (rest files)))
      (when close? (async/close! ch)))))

(comment
  (def release (latest-release))
  release
  ;; this is just for testing using an already downloaded release file
  (defn fake-download-url []
    (File. "/Users/mark/Downloads/nhspd-nov-2020.zip")))
