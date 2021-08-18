(ns com.eldrix.nhspd.coords
  "Provides functions to convert between geographical coordinate systems."
  (:import
    (uk.gov.dstl.geo.osgb OSGB36 NationalGrid EastingNorthingConversion Constants)))

(defn osgb36->wgs84*
  "Return a raw double array reflecting latitude and longitude of the
  coordinates specified."
  [easting northing]
  (let [latlon (EastingNorthingConversion/toLatLon (double-array [easting northing])
                                                   Constants/ELLIPSOID_AIRY1830_MAJORAXIS,
                                                   Constants/ELLIPSOID_AIRY1830_MINORAXIS,
                                                   Constants/NATIONALGRID_N0,
                                                   Constants/NATIONALGRID_E0,
                                                   Constants/NATIONALGRID_F0,
                                                   Constants/NATIONALGRID_LAT0,
                                                   Constants/NATIONALGRID_LON0)]
    (OSGB36/toWGS84 (aget latlon 0) (aget latlon 1))))

(defn osgb36->wgs84
  "Return a namespaced map containing WGS84 coordinates."
  [easting northing]
  (let [latlon (osgb36->wgs84* easting northing)]
    {:urn.ogc.def.crs.EPSG.4326/latitude  (aget latlon 0)
     :urn.ogc.def.crs.EPSG.4326/longitude (aget latlon 1)}))

(defn wgs84->osgb36* [lat lon]
  (EastingNorthingConversion/fromLatLon (OSGB36/fromWGS84 lat lon)
                                        Constants/ELLIPSOID_AIRY1830_MAJORAXIS,
                                        Constants/ELLIPSOID_AIRY1830_MINORAXIS,
                                        Constants/NATIONALGRID_N0,
                                        Constants/NATIONALGRID_E0,
                                        Constants/NATIONALGRID_F0,
                                        Constants/NATIONALGRID_LAT0,
                                        Constants/NATIONALGRID_LON0))

(defn wgs84->osgb36 [lat lon]
  (let [result (wgs84->osgb36* lat lon)]
    {:OSGB36/easting (int (aget result 0)) :OSGB36/northing (int (aget result 1))}))

(comment
  (osgb36->wgs84 317551 179319)
  (seq (osgb36->wgs84* 317551 179319))

  (wgs84->osgb36 51.50676433188346 -3.1893603167554194)

  (osgb36->wgs84 429157 623009)
  (seq (wgs84->osgb36* 55.5 -1.54))
  )

