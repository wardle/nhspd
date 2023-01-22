(ns com.eldrix.nhspd.search
  "An NHSPD file-based index providing postcode search functionality.
  This is currently using Apache Lucene as a key-value store but its indexing
  capabilities support future plans for better geographical queries."
  (:require [clojure.core.async :as a]
            [geocoordinates.core :as geo]
            [com.eldrix.nhspd.postcode :as pcode]
            [taoensso.nippy :as nippy])
  (:import (org.apache.lucene.index Term IndexWriter IndexWriterConfig DirectoryReader IndexWriterConfig$OpenMode IndexReader)
           (org.apache.lucene.store FSDirectory)
           (org.apache.lucene.document Document Field$Store StoredField StringField LatLonPoint)
           (org.apache.lucene.search IndexSearcher TermQuery TopDocs ScoreDoc)
           (org.apache.lucene.analysis.standard StandardAnalyzer)
           (java.nio.file Paths)))

(set! *warn-on-reflection* true)

(defn make-postcode-doc
  "Turn a postcode into a Lucene document."
  [{:strs [PCDS PCD2 OSNRTH1M OSEAST1M] :as pc}]
  (let [doc (doto (Document.)
              (.add (StringField. "pcds" ^String PCDS Field$Store/NO))
              (.add (StringField. "pcd2" ^String PCD2 Field$Store/NO))
              (.add (StoredField. "pc" ^bytes (nippy/freeze pc))))]
    (if (and OSNRTH1M OSEAST1M)
      (let [latlon (geo/easting-northing->latitude-longitude {:easting OSEAST1M :northing OSNRTH1M} :national-grid)]
        (doto doc (.add (LatLonPoint. "wgs84" (:latitude latlon) (:longitude latlon)))))
      doc)))

(defn write-batch! [^IndexWriter writer postcodes]
  (dorun (map #(.addDocument writer (make-postcode-doc %)) postcodes))
  (.commit writer))

(defn open-index-writer
  ^IndexWriter [filename]
  (let [analyzer (StandardAnalyzer.)
        directory (FSDirectory/open (Paths/get filename (into-array String [])))
        writer-config (doto (IndexWriterConfig. analyzer)
                        (.setOpenMode IndexWriterConfig$OpenMode/CREATE_OR_APPEND))]
    (IndexWriter. directory writer-config)))

(defn build-index
  "Build an index from NHSPD postcode file streamed on the channel specified."
  [ch out]
  (with-open [writer (open-index-writer out)]
    (a/<!!                                                  ;; block until pipeline complete
      (a/pipeline                                           ;; pipeline for side-effects
        (.availableProcessors (Runtime/getRuntime))         ;; Parallelism factor
        (doto (a/chan) (a/close!))                          ;; Output channel - /dev/null
        (map (partial write-batch! writer))
        ch))
    (.forceMerge writer 1)))

(defn open-index-reader
  ^IndexReader [filename]
  (let [directory (FSDirectory/open (Paths/get filename (into-array String [])))]
    (DirectoryReader/open directory)))

(defn fetch-postcode
  "Fetch the postcode `pc` specified.
   - pc : a UK postal code; will be coerced into the PCD2 postcode standard."
  [^IndexSearcher searcher pc]
  (when-let [pc' (pcode/normalize pc)]
    (when-let [score-doc ^ScoreDoc (first (seq (.-scoreDocs ^TopDocs (.search searcher (TermQuery. (Term. "pcd2" pc')) 1))))]
      (when-let [doc (.doc searcher (.-doc score-doc))]
        (nippy/thaw (.-bytes (.getBinaryValue doc "pc")))))))

(defn fetch-wgs84
  "Returns WGS84 geographical coordinates for a given postcode.
   - pc : a UK postal code; will be coerced into the PCD2 postcode standard."
  [^IndexSearcher searcher pc]
  (let [postcode (fetch-postcode searcher pc)
        osnrth1m (get postcode "OSNRTH1M")
        oseast1m (get postcode "OSEAST1M")]
    (when (and osnrth1m oseast1m)
      (let [latlon (geo/easting-northing->latitude-longitude {:easting oseast1m :northing osnrth1m} :national-grid)]
        (vector (:latitude latlon) (:longitude latlon))))))

(comment
  (make-postcode-doc {"PCDS" "CF14 4XW" "PCD2" "CF14  4XW" "OSEAST1M" 317551 "OSNRTH1M" 179319})

  (def reader (open-index-reader "/var/tmp/nhspd-nov-2020"))
  (def searcher (IndexSearcher. reader))
  (seq (.-scoreDocs (.search searcher (TermQuery. (Term. "version" "1.0")) 1)))
  (.numDocs reader)
  (def searcher (IndexSearcher. reader))
  (fetch-postcode searcher nil)
  (fetch-wgs84 searcher nil)

  (com.eldrix.nhspd.postcode/distance-between
    (fetch-postcode searcher "CF47 9DT")
    (fetch-postcode searcher "CF14 4XW"))
  (.close reader))
