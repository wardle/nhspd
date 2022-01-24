(ns com.eldrix.nhspd.coords
  (:require [geocoordinates.core :as geo]))

(defn osgb36->wgs84
  "Convert GB OS easting/northing to a map of WGS coordinates."
  [oseast1m osnrth1m]
  (when (and osnrth1m oseast1m)
    (let [latlon (geo/easting-northing->latitude-longitude {:easting oseast1m :northing osnrth1m} :national-grid)]
      {:urn.ogc.def.crs.EPSG.4326/latitude (:latitide latlon)
      :urn.ogc.def.crs.EPSG.4326/longitude (:longitude latlon)})))