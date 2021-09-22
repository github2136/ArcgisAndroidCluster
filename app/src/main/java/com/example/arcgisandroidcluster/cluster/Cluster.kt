package com.example.arcgisandroidcluster.cluster

import com.esri.arcgisruntime.mapping.view.Graphic

/**
 * 聚合的点
 */
class Cluster<T>(val latlng: UtilLatLng) {
      val clusterItems = mutableListOf<ClusterItem<T>>()
      var marker: Graphic? = null
}