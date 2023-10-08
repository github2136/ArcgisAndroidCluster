package com.example.arcgisandroidcluster.cluster

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.util.LruCache
import android.view.View
import com.esri.arcgisruntime.geometry.*
import com.esri.arcgisruntime.mapping.Viewpoint
import com.esri.arcgisruntime.mapping.view.*
import com.esri.arcgisruntime.symbology.*
import com.esri.arcgisruntime.util.ListChangedEvent
import com.esri.arcgisruntime.util.ListChangedListener
import com.example.arcgisandroidcluster.R
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.max

/**
 * Created by YB on 2021/9/7
 * 点聚合
 * @param clusterSize 聚合范围的大小（指点像素单位距离内的点会聚合到一个点显示）
 */
class ClusterSceneOverlay<T>(val context: Context, val sceneView: SceneView, val clusterSize: Int, val markerMap: MutableMap<String, Any>) : NavigationChangedListener, ViewpointChangedListener,
    ListChangedListener<Graphic> {
    private val markerHandlerThread = HandlerThread("addMarker") //添加marker线程
    private val signClusterThread = HandlerThread("calculateCluster") //计算聚合点线程
    private var markerHandler: Handler
    private var signClusterHandler: Handler
    private var lruCache: LruCache<Int, Symbol> = LruCache(80)
    private val clusterItems = mutableListOf<ClusterItem<T>>() //所有聚合点
    private val clusters = mutableListOf<Cluster<T>>() //已经聚合的点
    private val clusterOverlay = GraphicsOverlay()
    private var mIsCanceled = false
    private var PXInMeters = 0.0
    private val graphicsPoint: android.graphics.Point = android.graphics.Point()
    private var clusterDistance = 0.0
    var clusterRender: ClusterRender<T>? = null //聚合点样式
    var distanceLimit = -1 //距离限制
    var pointLimit: UtilLatLng? = null //距离限制
    // private var width = 0
    // private var height = 0

    init {
        //默认最多会缓存80张图片作为聚合显示元素图片,根据自己显示需求和app使用内存情况,可以修改数量
        // PXInMeters = getScale()
        clusterDistance = PXInMeters * clusterSize
        sceneView.graphicsOverlays.add(clusterOverlay)
        markerHandlerThread.start()
        signClusterThread.start()
        markerHandler = MarkerHandler(markerHandlerThread.looper)
        signClusterHandler = SignClusterHandler(signClusterThread.looper)
        sceneView.addNavigationChangedListener(this)
        sceneView.addViewpointChangedListener(this)
        clusterOverlay.graphics.addListChangedListener(this)
        assignClusters()
    }

    private val LINEAR_UNIT_METERS = LinearUnit(LinearUnitId.METERS)
    private fun getScale(): Double {
        val maxScaleBarLengthPixels = sceneView.width / 4
        val centerX = sceneView.width / 2
        val centerY = sceneView.height / 2
        graphicsPoint.set((centerX - maxScaleBarLengthPixels / 2), centerY)
        val p1 = sceneView.screenToLocationAsync(graphicsPoint).get()
        graphicsPoint.set((centerX + maxScaleBarLengthPixels / 2), centerY)
        val p2 = sceneView.screenToLocationAsync(graphicsPoint).get()
        val builder = PolylineBuilder(sceneView.spatialReference)
        builder.addPoint(p1)
        builder.addPoint(p2)
        val maxLengthGeodetic = GeometryEngine.lengthGeodetic(
            builder.toGeometry(),
            LINEAR_UNIT_METERS,
            GeodeticCurveType.GEODESIC
        )

        val scale = maxLengthGeodetic / maxScaleBarLengthPixels //每个像素所占米数
        return scale
    }

    /**
     * 添加一个聚合点
     */
    fun addClusterItem(item: ClusterItem<T>) {
        val message = Message.obtain()
        message.what = CALCULATE_SINGLE_CLUSTER
        message.obj = item
        signClusterHandler.sendMessage(message)
    }

    /**
     * 添加大量聚合点
     */
    fun addClusterItems(items: List<ClusterItem<T>>) {
        val message = Message.obtain()
        message.what = CALCULATE_MULTIPLE_CLUSTER
        message.obj = items
        signClusterHandler.sendMessage(message)
    }

    fun refresh() {
        assignClusters()
    }

    fun clearClusterItem() {
        clusters.clear()
        clusterItems.clear()
        clusterOverlay.graphics.clear()
    }

    /**
     * 显示隐藏图标
     */
    fun setVisible(visible: Boolean) {
        clusterOverlay.isVisible = visible
        if (visible) {
            assignClusters()
        }
    }

    fun visibleArea(): Envelope? {
        return try {
            sceneView.getCurrentViewpoint(Viewpoint.Type.BOUNDING_GEOMETRY).targetGeometry.extent
        } catch (e: Exception) {
            return null
        }
    }

    private var xMin = ""
    private var xMax = ""
    private var yMin = ""
    private var yMax = ""
    override fun navigationChanged(p0: NavigationChangedEvent?) {
        if (!sceneView.isNavigating) {
            val area = visibleArea()
            area?.let {
                val xMinTemp = area.xMin.latlngFormat()
                val xMaxTemp = area.xMax.latlngFormat()
                val yMinTemp = area.yMin.latlngFormat()
                val yMaxTemp = area.yMax.latlngFormat()
                if ((xMin != xMinTemp || xMax != xMaxTemp || yMin != yMinTemp || yMax != yMaxTemp)) {
                    xMin = xMinTemp
                    xMax = xMaxTemp
                    yMin = yMinTemp
                    yMax = yMaxTemp
                    PXInMeters = getScale()
                    clusterDistance = PXInMeters * clusterSize
                    if (clusterOverlay.isVisible) {
                        assignClusters()
                    }
                }
            }
        }
    }

    override fun viewpointChanged(p0: ViewpointChangedEvent?) {
        if (!sceneView.isNavigating) {
            val area = visibleArea()
            area?.let {
                val xMinTemp = area.xMin.latlngFormat()
                val xMaxTemp = area.xMax.latlngFormat()
                val yMinTemp = area.yMin.latlngFormat()
                val yMaxTemp = area.yMax.latlngFormat()
                if ((xMin != xMinTemp || xMax != xMaxTemp || yMin != yMinTemp || yMax != yMaxTemp)) {
                    xMin = xMinTemp
                    xMax = xMaxTemp
                    yMin = yMinTemp
                    yMax = yMaxTemp
                    PXInMeters = getScale()
                    clusterDistance = PXInMeters * clusterSize
                    if (clusterOverlay.isVisible) {
                        assignClusters()
                    }
                }
            }
        }
    }

    private fun assignClusters() {
        mIsCanceled = true
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
        val key = UUID.randomUUID().toString()
        graphic.attributes["KEY"] = key
        markerMap[key] = cluster
        cluster.marker = graphic
        clusterOverlay.graphics.add(graphic)
    }

    private val green by lazy { context.getColor(R.color.colorTextGreen) }
    private val blue by lazy { context.getColor(R.color.colorTextBlue) }

    /**
     * 获取每个聚合点的绘制样式
     */
    private fun getSymbol(clusterItem: MutableList<ClusterItem<T>>): Symbol {
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
        val visibleBounds = visibleArea()
        visibleBounds?.let {
            val latlng: UtilLatLng = clusterItem.latLng
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
     * 在已有的聚合基础上，对添加的多个元素进行聚合
     */
    private fun calculateMultipleCluster(clusterItems: List<ClusterItem<T>>) {
        val visibleBounds = visibleArea()
        visibleBounds?.let {
            for (clusterItem in clusterItems) {
                val latlng: UtilLatLng = clusterItem.latLng
                if (!contains(visibleBounds, Point(latlng.lng, latlng.lat))) {
                    continue
                }
                var cluster: Cluster<T>? = getCluster(latlng, clusters)
                if (cluster != null) {
                    cluster.clusterItems.add(clusterItem)
                } else {
                    cluster = Cluster(latlng)
                    clusters.add(cluster)
                    cluster.clusterItems.add(clusterItem)
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
    }

    /**
     * 根据一个点获取是否可以依附的聚合点，没有则返回null
     *
     * @param latLng
     * @return
     */
    private fun getCluster(latLng: UtilLatLng, clusters: List<Cluster<T>>): Cluster<T>? {
        for (cluster in clusters) {
            val clusterCenterPoint: UtilLatLng = cluster.latlng
            val distance: Float = UtilLatLng.calculateLineDistance(latLng, clusterCenterPoint)
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
        val visibleBounds = visibleArea()
        for (clusterItem in clusterItems) {
            if (mIsCanceled) {
                return
            }
            val latlng: UtilLatLng = clusterItem.latLng
            if (visibleBounds != null) {
                if (contains(visibleBounds, Point(latlng.lng, latlng.lat))) {
                    if (pointLimit != null && distanceLimit != -1) {
                        if ((UtilLatLng.calculateLineDistance(pointLimit!!, latlng) > distanceLimit)) {
                            continue
                        }
                    }
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
    inner class MarkerHandler(looper: Looper) : Handler(looper) {
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
    inner class SignClusterHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(message: Message) {
            when (message.what) {
                CALCULATE_CLUSTER -> calculateClusters()
                CALCULATE_SINGLE_CLUSTER -> {
                    val item = message.obj as ClusterItem<T>
                    clusterItems.add(item)
                    calculateSingleCluster(item)
                }
                CALCULATE_MULTIPLE_CLUSTER -> {
                    val item = message.obj as List<ClusterItem<T>>
                    clusterItems.addAll(item)
                    calculateMultipleCluster(item)
                }
            }
        }
    }

    fun contains(envelope: Envelope, point: Point): Boolean {
        val extent = envelope
        return point.x > extent.xMin && point.x < extent.xMax && point.y > extent.yMin && point.y < extent.yMax
    }


    override fun listChanged(event: ListChangedEvent<Graphic>) {
        if (event.action == ListChangedEvent.Action.REMOVED) {
            event.items.forEach { g ->
                g.attributes["KEY"]?.apply {
                    markerMap.remove(this)
                }
            }
        }
    }

    companion object {
        val ADD_CLUSTER_LIST = 0 //添加标记集合
        val ADD_SINGLE_CLUSTER = 1 //添加单个标记
        val UPDATE_SINGLE_CLUSTER = 2 //更新单个现有标记

        val CALCULATE_CLUSTER = 0 //计算聚合点
        val CALCULATE_SINGLE_CLUSTER = 1 //添加单个记录
        val CALCULATE_MULTIPLE_CLUSTER = 2 //添加多个记录
    }
}