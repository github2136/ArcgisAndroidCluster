package com.example.arcgisandroidcluster.cluster

import com.esri.arcgisruntime.mapping.view.Graphic
import com.example.arcgisandroidcluster.GPSUtil

/**
 * 聚合的点
 */
class Cluster<T>(val latlng: GPSUtil.UtilLatLng) {
      val clusterItems = mutableListOf<ClusterItem<T>>()
      var marker: Graphic? = null
}