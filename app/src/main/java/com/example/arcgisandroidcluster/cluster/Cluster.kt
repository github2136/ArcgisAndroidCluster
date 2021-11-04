package com.example.arcgisandroidcluster.cluster

import com.esri.arcgisruntime.mapping.view.Graphic

/**
 * Created by YB on 2021/9/7
 * 聚合的点
 */
class Cluster<T>(val latlng: UtilLatLng) {
    val clusterItems = mutableListOf<ClusterItem<T>>()
    var marker: Graphic? = null
}