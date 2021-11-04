package com.example.arcgisandroidcluster.cluster

/**
 * Created by YB on 2021/9/7
 * 聚合前的点
 */
data class ClusterItem<T>(val latLng: UtilLatLng, val obj: T)