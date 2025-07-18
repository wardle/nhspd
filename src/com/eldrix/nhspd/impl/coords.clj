(ns com.eldrix.nhspd.impl.coords
  (:require [geocoordinates.core :as geo]))

(defn osgb36->wgs84
  "Convert GB OS easting/northing to a map of WGS coordinates."
  [oseast1m osnrth1m]
  (when (and osnrth1m oseast1m)
    (let [latlon (geo/easting-northing->latitude-longitude {:easting oseast1m :northing osnrth1m} :national-grid)]
      {:urn.ogc.def.crs.EPSG.4326/latitude  (:latitude latlon)
       :urn.ogc.def.crs.EPSG.4326/longitude (:longitude latlon)})))

(defn with-wgs84
  "Returns NHSPD postcode supplemented with WGS84 coordinates when possible."
  [{:keys [OSEAST1M OSNRTH1M] :as pc}]
  (if (and OSEAST1M OSNRTH1M)
    (let [{:keys [latitude longitude]}
          (geo/easting-northing->latitude-longitude {:easting OSEAST1M :northing OSEAST1M} :national-grid)]
      (assoc pc :WGS84LAT latitude :WGS84LNG longitude))
    pc))