package com.example.arcgisandroidcluster.cluster


/**
 * 聚合前的点
 */
data class ClusterItem<T>(val latLng: UtilLatLng, val obj: T)