package com.example.arcgisandroidcluster.cluster

import com.example.arcgisandroidcluster.GPSUtil

/**
 * 聚合前的点
 */
data class ClusterItem<out T>(val latLng: GPSUtil.UtilLatLng, val obj: T)