package com.example.arcgisandroidcluster.cluster

import com.esri.arcgisruntime.symbology.Symbol

/**
 * 聚合点样式
 */
interface ClusterRender<T> {

    /**
     * 单聚合点是否缓存
     */
    fun cacheOne(): Boolean

    /**
     * 自定义点聚合样式
     */
    fun getSymbol(clusterItem: MutableList<ClusterItem<T>>): Symbol
}