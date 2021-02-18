(ns com.eldrix.nhspd.coords
  "Provides functions to convert between geographical coordinate systems."
  (:import (org.apache.sis.geometry DirectPosition2D)
           (org.apache.sis.referencing CRS)))

(defn make-osgb36->wgs84-fn
  "Returns a function that can convert OSGB36 easting/northing to WGS84 coords."
  []
  (let [osgb36 (CRS/forCode "EPSG:27700")
        wgs84 (CRS/forCode "EPSG:4326")
        op (CRS/findOperation osgb36 wgs84 nil)
        transformer (.getMathTransform op)]
    (fn ^doubles [easting northing]
      (.getCoordinate (.transform transformer (DirectPosition2D. easting northing) nil)))))

(defn make-wgs84->osgb36-fn
  "Returns a function that can convert WGS84 coords to OSGB36 easting/northing."
  []
  (let [osgb36 (CRS/forCode "EPSG:27700")
        wgs84 (CRS/forCode "EPSG:4326")
        op (CRS/findOperation wgs84 osgb36 nil)
        transformer (.getMathTransform op)]
    (fn ^doubles [latitude longitude]
      (.getCoordinate (.transform transformer (DirectPosition2D. latitude longitude) nil)))))

(def raw-wgs84->osgb36 (delay (make-wgs84->osgb36-fn)))
(def raw-osgb36->wgs84 (delay (make-osgb36->wgs84-fn)))

(defn wgs84->osgb36
  [latitude longitude]
  (let [result (@raw-wgs84->osgb36 latitude longitude)]
    {:OSGB36/easting (aget result 0) :OSGB36/northing (aget result 1)}))

(defn osgb36->wgs84
  [easting northing]
  (let [result (@raw-osgb36->wgs84 easting northing)]
    {:urn.ogc.def.crs.EPSG.4326/latitude (aget result 0) :urn.ogc.def.crs.EPSG.4326/longitude (aget result 1)}))

(comment
  (osgb36->wgs84 317551 179319)
  (wgs84->osgb36 51.50676433188346 -3.1893603167554194)
  )

