package com.example.arcgisandroidcluster.cluster

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.util.LruCache
import com.esri.arcgisruntime.geometry.*
import com.esri.arcgisruntime.mapping.view.*
import com.esri.arcgisruntime.symbology.*
import com.example.arcgisandroidcluster.*

/**
 * 点聚合
 * @param clusterSize 聚合范围的大小（指点像素单位距离内的点会聚合到一个点显示）
 */
class ClusterOverlay<T>(val context: Context, val mapView: MapView, val clusterSize: Int, val type: String) : NavigationChangedListener, ViewpointChangedListener {
    private val mJsonUtil by lazy { JsonUtil.instance }
    private val markerHandlerThread = HandlerThread("addMarker")
    private val signClusterThread = HandlerThread("calculateCluster")
    private var markerHandler: Handler
    private var signClusterHandler: Handler
    private var lruCache: LruCache<Int, Symbol> = LruCache(80)
    private val clusterItems = mutableListOf<ClusterItem<T>>()//所有聚合点
    private val clusters = mutableListOf<Cluster<T>>()//已经聚合的点
    private val clusterOverlay = GraphicsOverlay()
    private var mIsCanceled = false
    private var PXInMeters = 0.0
    private val graphicsPoint: android.graphics.Point = android.graphics.Point()
    private var clusterDistance = 0.0
    var clusterRender: ClusterRender<T>? = null//聚合点样式

    init {
        //默认最多会缓存80张图片作为聚合显示元素图片,根据自己显示需求和app使用内存情况,可以修改数量
        PXInMeters = getScale()
        clusterDistance = PXInMeters * clusterSize
        mapView.graphicsOverlays.add(clusterOverlay)
        mapView.addNavigationChangedListener(this)
        mapView.addViewpointChangedListener(this)
        markerHandlerThread.start()
        signClusterThread.start()
        markerHandler = MarkerHandler(markerHandlerThread.looper)
        signClusterHandler = SignClusterHandler(signClusterThread.looper)
        assignClusters()
    }

    private val LINEAR_UNIT_METERS = LinearUnit(LinearUnitId.METERS)
    private fun getScale(): Double {
        val maxScaleBarLengthPixels = mapView.width / 4
        val centerX = mapView.width / 2
        val centerY = mapView.height / 2
        graphicsPoint.set((centerX - maxScaleBarLengthPixels / 2), centerY)
        val p1 = mapView.screenToLocation(graphicsPoint)
        graphicsPoint.set((centerX + maxScaleBarLengthPixels / 2), centerY)
        val p2 = mapView.screenToLocation(graphicsPoint)
        val visibleArea = mapView.visibleArea
        if (p1 == null || p2 == null || visibleArea == null) {
            return 0.0
        }
        val centerPoint = visibleArea.extent.center
        val builder = PolylineBuilder(mapView.spatialReference)
        builder.addPoint(p1)
        builder.addPoint(centerPoint)
        builder.addPoint(p2)
        val maxLengthGeodetic = GeometryEngine.lengthGeodetic(
            builder.toGeometry(),
            LINEAR_UNIT_METERS,
            GeodeticCurveType.GEODESIC
        )

        val scale = maxLengthGeodetic / maxScaleBarLengthPixels//每个像素所占米数
        return scale
    }

    /**
     * 添加一个聚合点
     *
     * @param item
     */
    fun addClusterItem(item: ClusterItem<T>) {
        val message = Message.obtain()
        message.what = CALCULATE_SINGLE_CLUSTER
        message.obj = item
        signClusterHandler.sendMessage(message)
    }

    fun clearClusterItem() {
        clusterItems.clear()
        clusterOverlay.graphics.clear()
    }

    /**
     * 显示隐藏图标
     */
    fun setVisible(visible: Boolean) {
        clusterOverlay.isVisible = visible
    }

    private var xMin = ""
    private var xMax = ""
    private var yMin = ""
    private var yMax = ""
    override fun navigationChanged(p0: NavigationChangedEvent?) {
        val xMinTemp = mapView.visibleArea.extent.xMin.latlngFormat()
        val xMaxTemp = mapView.visibleArea.extent.xMax.latlngFormat()
        val yMinTemp = mapView.visibleArea.extent.yMin.latlngFormat()
        val yMaxTemp = mapView.visibleArea.extent.yMax.latlngFormat()
        if ((xMin != xMinTemp || xMax != xMaxTemp || yMin != yMinTemp || yMax != yMaxTemp) && !mapView.isNavigating) {
            xMin = xMinTemp
            xMax = xMaxTemp
            yMin = yMinTemp
            yMax = yMaxTemp
            PXInMeters = getScale()
            clusterDistance = PXInMeters * clusterSize
            assignClusters()
        }
    }

    override fun viewpointChanged(p0: ViewpointChangedEvent?) {
        val xMinTemp = mapView.visibleArea.extent.xMin.latlngFormat()
        val xMaxTemp = mapView.visibleArea.extent.xMax.latlngFormat()
        val yMinTemp = mapView.visibleArea.extent.yMin.latlngFormat()
        val yMaxTemp = mapView.visibleArea.extent.yMax.latlngFormat()
        if ((xMin != xMinTemp || xMax != xMaxTemp || yMin != yMinTemp || yMax != yMaxTemp) && !mapView.isNavigating) {
            xMin = xMinTemp
            xMax = xMaxTemp
            yMin = yMinTemp
            yMax = yMaxTemp
            PXInMeters = getScale()
            clusterDistance = PXInMeters * clusterSize
            assignClusters()
        }
    }

    private fun assignClusters() {
        signClusterHandler.removeMessages(CALCULATE_CLUSTER)
        signClusterHandler.sendEmptyMessage(CALCULATE_CLUSTER)
    }

    /**
     * 将聚合元素添加至地图上
     */
    private fun addClusterToMap(clusters: List<Cluster<T>>) {
        clusterOverlay.graphics.clear()
        for (cluster in clusters) {
            addSingleClusterToMap(cluster)
        }
    }

    /**
     * 将单个聚合元素添加至地图显示
     *
     * @param cluster
     */
    private fun addSingleClusterToMap(cluster: Cluster<T>) {
        val graphic = Graphic(Point(cluster.latlng.lng, cluster.latlng.lat, SpatialReferences.getWgs84()), getSymbol(cluster.clusterItems))
        graphic.attributes["NAME"] = cluster.javaClass.name
        graphic.attributes["OBJ"] = mJsonUtil.getGson().toJson(cluster)
        graphic.attributes["TYPE"] = type
        cluster.marker = graphic
        clusterOverlay.graphics.add(graphic)
    }

    private val green by lazy { context.getColor(R.color.colorTextGreen) }
    private val blue by lazy { context.getColor(R.color.colorTextBlue) }

    /**
     * 获取每个聚合点的绘制样式
     */
    private fun getSymbol(clusterItem: MutableList<ClusterItem<T>>): Symbol? {
        val num = clusterItem.size
        var symbol: Symbol? = lruCache.get(num)
        if (symbol == null) {
            if (clusterRender != null) {
                symbol = clusterRender!!.getSymbol(clusterItem)
                if (num > 1 || clusterRender!!.cacheOne()) {
                    lruCache.put(num, symbol)
                }
            } else {
                val text = if (num > 1) num.toString() else ""
                // 文字绘制
                val textSymbol = TextSymbol(15f, text, Color.BLACK, TextSymbol.HorizontalAlignment.CENTER, TextSymbol.VerticalAlignment.MIDDLE)
                // 圆点
                val backgroundSymbol = SimpleMarkerSymbol(SimpleMarkerSymbol.Style.CIRCLE, green, 30f)
                //边线
                val outlineSymbol = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, blue, 2f)
                backgroundSymbol.outline = outlineSymbol
                symbol = CompositeSymbol(listOf(backgroundSymbol, textSymbol))
                lruCache.put(num, symbol)
            }
        }
        return symbol
    }

    /**
     * 在已有的聚合基础上，对添加的单个元素进行聚合
     *
     * @param clusterItem
     */
    private fun calculateSingleCluster(clusterItem: ClusterItem<T>) {
        val visibleBounds: Polygon? = mapView.visibleArea
        visibleBounds?.let {
            val latlng: GPSUtil.UtilLatLng = clusterItem.latLng
            if (!contains(visibleBounds, Point(latlng.lng, latlng.lat))) {
                return
            }
            var cluster: Cluster<T>? = getCluster(latlng, clusters)
            if (cluster != null) {
                cluster.clusterItems.add(clusterItem)
                val message = Message.obtain()
                message.what = UPDATE_SINGLE_CLUSTER
                message.obj = cluster
                markerHandler.removeMessages(UPDATE_SINGLE_CLUSTER)
                markerHandler.sendMessageDelayed(message, 5)
            } else {
                cluster = Cluster(latlng)
                clusters.add(cluster)
                cluster.clusterItems.add(clusterItem)
                val message = Message.obtain()
                message.what = ADD_SINGLE_CLUSTER
                message.obj = cluster
                markerHandler.sendMessage(message)
            }
        }
    }

    /**
     * 根据一个点获取是否可以依附的聚合点，没有则返回null
     *
     * @param latLng
     * @return
     */
    private fun getCluster(latLng: GPSUtil.UtilLatLng, clusters: List<Cluster<T>>): Cluster<T>? {
        for (cluster in clusters) {
            val clusterCenterPoint: GPSUtil.UtilLatLng = cluster.latlng
            val distance: Float = AMapUtils.calculateLineDistance(latLng, clusterCenterPoint)
            if (distance < clusterDistance) {
                return cluster
            }
        }
        return null
    }

    /**
     * 更新已加入地图聚合点的样式
     */
    private fun updateCluster(cluster: Cluster<T>) {
        cluster.marker?.apply {
            symbol = getSymbol(cluster.clusterItems)
        }
    }

    private fun calculateClusters() {
        mIsCanceled = false
        clusters.clear()
        val visibleBounds = mapView.visibleArea
        for (clusterItem in clusterItems) {
            if (mIsCanceled) {
                return
            }
            val latlng: GPSUtil.UtilLatLng = clusterItem.latLng

            if (contains(visibleBounds, Point(latlng.lng, latlng.lat))) {
                var cluster: Cluster<T>? = getCluster(latlng, clusters)
                if (cluster != null) {
                    cluster.clusterItems.add(clusterItem)
                } else {
                    cluster = Cluster(latlng)
                    clusters.add(cluster)
                    cluster.clusterItems.add(clusterItem)
                }
            }
        }

        //复制一份数据，规避同步
        val clusters: MutableList<Cluster<T>> = ArrayList()
        clusters.addAll(this.clusters)
        val message = Message.obtain()
        message.what = ADD_CLUSTER_LIST
        message.obj = clusters
        if (mIsCanceled) {
            return
        }
        markerHandler.sendMessage(message)
    }

    /**
     * 处理market添加，更新等操作
     */
    inner class MarkerHandler(looper: Looper?) : Handler(looper) {
        override fun handleMessage(message: Message) {
            when (message.what) {
                ADD_CLUSTER_LIST -> {
                    val clusters = message.obj as List<Cluster<T>>
                    addClusterToMap(clusters)
                }
                ADD_SINGLE_CLUSTER -> {
                    val cluster = message.obj as Cluster<T>
                    addSingleClusterToMap(cluster)
                }
                UPDATE_SINGLE_CLUSTER -> {
                    val updateCluster = message.obj as Cluster<T>
                    updateCluster(updateCluster)
                }
            }
        }
    }

    /**
     * 处理聚合点算法线程
     */
    inner class SignClusterHandler(looper: Looper?) : Handler(looper) {
        override fun handleMessage(message: Message) {
            when (message.what) {
                CALCULATE_CLUSTER -> calculateClusters()
                CALCULATE_SINGLE_CLUSTER -> {
                    val item = message.obj as ClusterItem<T>
                    clusterItems.add(item)
                    calculateSingleCluster(item)
                }
            }
        }
    }

    fun contains(polygon: Polygon, point: Point): Boolean {
        val extent = polygon.extent
        return point.x > extent.xMin && point.x < extent.xMax && point.y > extent.yMin && point.y < extent.yMax
    }

    companion object {
        val ADD_CLUSTER_LIST = 0
        val ADD_SINGLE_CLUSTER = 1
        val UPDATE_SINGLE_CLUSTER = 2

        val CALCULATE_CLUSTER = 0
        val CALCULATE_SINGLE_CLUSTER = 1
    }

}