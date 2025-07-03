(ns com.eldrix.nhspd.download
  (:require [clojure.core.async :as a]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [com.eldrix.nhspd.parse :as parse]
            [clojure.tools.logging :as log]
            [hato.client :as hc])
  (:import (java.io File)
           (java.time LocalDate)
           (java.util.zip ZipFile)))

(defn download-url
  "Download a file from the URL specified.
  Parameters:
  - url : the URL to fetch
  - out   : (optional) the java.io.File/Writer/OutputStream to which to write.
  If no `out` specified, a temporary file will be created and returned."
  ([url] (download-url url (File/createTempFile "nhspd" ".zip")))
  ([url out]
   (io/copy (:body (hc/get url {:as :stream :http-client {:redirect-policy :always}})) out)
   out))

(defn get-latest-release
  "Returns information about the latest NHSPD release.
   At the moment, there is no available API for this, so we fake by using a
   local embedded datafile that can be updated with the latest release date
  and URL. The NHSPD is currently released quarterly."
  []
  (let [releases (map #(assoc % :date (LocalDate/parse (:date %)))
                      (edn/read-string (slurp (io/resource "nhspd.edn"))))]
    (first (reverse (sort-by :date releases)))))

(defn stream-release
  "Blocking; stream postcode data from the zip file to the channel.
  Parameters:
  - f      : filename or java.io.File for the NHSPD zip file.
  - ch     : clojure.core.async channel
  - close? : optional; whether to close the channel when done."
  ([f ch] (stream-release f ch true))
  ([f ch close?]
   (with-open [zf (ZipFile. f)]
     (if-let [all-pcodes-file (first (filter #(re-matches #"Data/nhg.*\.csv" (.getName %)) (enumeration-seq (.entries zf))))]
       (parse/import-postcodes (.getInputStream zf all-pcodes-file) ch close?)
       (throw (ex-info "Unable to find postcode file in archive. We looked for a file 'nhg*.csv'. Has archive format changed?" {}))))))

(defn stream-latest-release
  "Blocking; streams the latest release of the NHSPD.

  As we manually track the latest release in resources/nhspd.edn because
  as far as I know there's no download API, only a portal, we log a warning
  if the latest release is more than 3 months in the past."
  ([ch] (stream-latest-release ch true true))
  ([ch delete?] (stream-latest-release ch delete? true))
  ([ch delete? close?]
   (let [release (get-latest-release)
         possibly-outdated? (.isAfter (LocalDate/now) (.plusMonths (:date release) 3))
         _ (log/info "Downloading NHSPD release" (:date release) "from" (:url release))
         _ (when possibly-outdated? (log/warn "Latest known NHSPD release more than 3 months old; metadata likely needs updating.")
                                    (log/warn "Raise an issue on https://github.com/wardle/nhspd/issues"))
         downloaded (download-url (:url release))]
     (log/info "Download complete. Importing data")
     (stream-release downloaded ch close?)
     (log/info "Import complete.")
     (when delete? (.delete downloaded)))))

(comment
  (def release (get-latest-release))
  release
  ;; this is just for testing using an already downloaded release file
  (defn fake-download-url []
    (File. "/Users/mark/Downloads/nhspd-nov-2020.zip"))

  (def downloaded (fake-download-url))
  (def downloaded (download-url (:url release)))
  downloaded

  (def ch (a/chan))
  (a/thread (stream-release downloaded ch))
  (a/<!! ch))
