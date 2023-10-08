package com.example.arcgisandroidcluster.cluster

import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.SpatialReferences

data class UtilLatLng(
    /**纬度-90,90*/
    var lat: Double,
    /**经度-180,180*/
    var lng: Double
) {
    fun getPoint() = Point(lng, lat, SpatialReferences.getWgs84())

    companion object {

        fun getLatLng(point: Point) = UtilLatLng(point.y, point.x)

        /**
         * 两点距离计算
         */
        fun calculateLineDistance(var0: UtilLatLng, var1: UtilLatLng): Float {
            try {
                var var2: Double = var0.lng
                var var4: Double = var0.lat
                var var6: Double = var1.lng
                var var8: Double = var1.lat
                var2 *= 0.01745329251994329
                var4 *= 0.01745329251994329
                var6 *= 0.01745329251994329
                var8 *= 0.01745329251994329
                val var10 = Math.sin(var2)
                val var12 = Math.sin(var4)
                val var14 = Math.cos(var2)
                val var16 = Math.cos(var4)
                val var18 = Math.sin(var6)
                val var20 = Math.sin(var8)
                val var22 = Math.cos(var6)
                val var24 = Math.cos(var8)
                val var26 = DoubleArray(3)
                val var27 = DoubleArray(3)
                var26[0] = var16 * var14
                var26[1] = var16 * var10
                var26[2] = var12
                var27[0] = var24 * var22
                var27[1] = var24 * var18
                var27[2] = var20
                val var28 = Math.sqrt(
                    (var26[0] - var27[0]) * (var26[0] - var27[0]) + (var26[1] - var27[1]) * (var26[1] - var27[1]) + (var26[2] - var27[2]) * (var26[2] - var27[2])
                )
                return (Math.asin(var28 / 2.0) * 1.27420015798544E7).toFloat()
            } catch (var30: Throwable) {
                var30.printStackTrace()
                return 0.0f
            }
        }

        /**
         * 3857转4326
         */
        fun mercator2latlng(latlng: UtilLatLng): UtilLatLng {
            var lng = latlng.lng / 20037508.34 * 180
            var lat = latlng.lat / 20037508.34 * 180
            lat = 180 / Math.PI * (2 * Math.atan(Math.exp(lat * Math.PI / 180)) - Math.PI / 2)
            return UtilLatLng(lat, lng)
        }
        /**
         * 4326转3857
         */
        fun latlng2mercator(latlng: UtilLatLng): UtilLatLng {
            var earthRad = 6378137.0
            var lng = latlng.lng * Math.PI / 180 * earthRad
            var a = latlng.lat * Math.PI / 180
            var lat = earthRad / 2 * Math.log((1.0 + Math.sin(a)) / (1.0 - Math.sin(a)))
            return UtilLatLng(lat, lng)
        }
    }
}

/**
 * 坐标格式化限制长度
 */
fun Double.latlngFormat(): String {
    return String.format("%.6f", this)
}