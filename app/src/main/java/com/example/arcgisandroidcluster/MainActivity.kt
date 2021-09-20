package com.example.arcgisandroidcluster

import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.text.TextPaint
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.esri.arcgisruntime.geometry.GeometryEngine
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.SpatialReferences
import com.esri.arcgisruntime.layers.Layer
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.Basemap
import com.esri.arcgisruntime.mapping.Viewpoint
import com.esri.arcgisruntime.mapping.view.*
import com.esri.arcgisruntime.symbology.PictureMarkerSymbol
import com.esri.arcgisruntime.symbology.Symbol
import com.example.arcgisandroidcluster.cluster.Cluster
import com.example.arcgisandroidcluster.cluster.ClusterItem
import com.example.arcgisandroidcluster.cluster.ClusterOverlay
import com.example.arcgisandroidcluster.cluster.ClusterRender
import com.google.gson.reflect.TypeToken
import kotlin.math.roundToInt
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    val mapView by lazy { findViewById<MapView>(R.id.mvMap) }

    private val mJsonUtil by lazy { JsonUtil.instance }

    //自定义样式
    val textFillPaint by lazy {
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 16f.sp2px.toFloat()
            color = Color.WHITE
            style = Paint.Style.FILL
        }
    }
    val circleWidth by lazy { 12f.dp2px.toFloat() }
    val circlePaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
            style = Paint.Style.FILL
        }
    }
    private val clusterOverlay by lazy {
        ClusterOverlay<D>(this, mapView, 45f.dp2px, D::class.java.name).apply {
                clusterRender = object : ClusterRender<D> {
                    override fun getSymbol(clusterItem: MutableList<ClusterItem<D>>): Symbol {
                        var num = clusterItem.size
                        num = if (num > 99) 99 else num
                        val resource = if (num > 1) {
                            val text = num.toString()
                            val drawable = resources.getDrawable(R.drawable.ic_marker_red, theme)
                            val b = Bitmap.createBitmap(drawable.intrinsicWidth + circleWidth.toInt(), drawable.intrinsicHeight + circleWidth.toInt(), Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(b)
                            drawable.setBounds(0, circleWidth.toInt(), canvas.width - circleWidth.toInt(), canvas.height)
                            drawable.draw(canvas)
                            val rect = Rect()
                            textFillPaint.getTextBounds(text, 0, text.length, rect)
                            val w = textFillPaint.measureText(text)
                            val circleX = canvas.width - circleWidth
                            val circleY = circleWidth
                            canvas.drawCircle(circleX, circleY, circleWidth, circlePaint)
                            canvas.drawText(num.toString(), circleX - w / 2, circleY + rect.height() / 2, textFillPaint)
                            BitmapDrawable(resources, b)
                        } else {
                            val drawable = resources.getDrawable(R.drawable.ic_marker_red, theme)
                            val b = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(b)
                            drawable.setBounds(0, 0, canvas.width, canvas.height)
                            drawable.draw(canvas)
                            BitmapDrawable(resources, b)
                        }
                        return PictureMarkerSymbol(resource as BitmapDrawable)
                    }

                    override fun cacheOne() = true
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setMapDefSetting()
        initPoint()
    }

    /**
     * 设置地图
     */
    fun setMapDefSetting() {
        mapView.isAttributionTextVisible = false
        //禁止旋转
        mapView.onTouchListener = object : DefaultMapViewOnTouchListener(this, mapView) {
            override fun onRotate(event: MotionEvent?, rotationAngle: Double): Boolean {
                return false
            }

            override fun onSingleTapUp(motionEvent: MotionEvent?): Boolean {
                if (motionEvent != null) {
                    //获取点击的坐标
                    val screenPoint = android.graphics.Point(
                        motionEvent.x.roundToInt(),
                        motionEvent.y.roundToInt()
                    )
                    // 点击坐标点转换经纬度
                    val mapPoint = mapView.screenToLocation(screenPoint)
                    // 经纬度转WGS84坐标
                    val wgs84Point =
                        GeometryEngine.project(mapPoint, SpatialReferences.getWgs84()) as Point
                    val listenable =
                        mapView.identifyGraphicsOverlaysAsync(screenPoint, 8.0, false, 1)
                    var clickMarker = false
                    listenable.addDoneListener {
                        val identifyLayerResults = listenable.get()
                        //循环图层
                        loop@ for (identifyLayerResult in identifyLayerResults) {
                            //循环所点击要素
                            for (geoElement in identifyLayerResult.graphics) {
                                clickMarker = true
                                onMarkerClick(geoElement)
                                break@loop
                            }
                        }
                    }
                    if (!clickMarker) {
                        onMapClick(wgs84Point)
                    }
                }
                return super.onSingleTapUp(motionEvent)
            }
        }
        mapView.addNavigationChangedListener { event ->
            onNavigationChanged(event)
        }
        mapView.addMapScaleChangedListener { event ->
            onMapScaleChanged(event)
        }
        val baseLayout = mutableListOf<Layer>()
        baseLayout.add(
            CacheTianDiTuTiledLayer.get(
                this,
                CacheTianDiTuTiledLayer.LayerType.TIANDITU_VECTOR_2000
            )
        )
        baseLayout.add(
            CacheTianDiTuTiledLayer.get(
                this,
                CacheTianDiTuTiledLayer.LayerType.TIANDITU_VECTOR_2000_LABLE
            )
        )
        mapView.map = ArcGISMap(Basemap().apply { this.baseLayers.addAll(baseLayout) })

        // 设置放大最大比例
        mapView.map.maxScale = 1000.0
        // 设置缩小最小比例
        mapView.map.minScale = 10000000.0
        mapView.setViewpoint(Viewpoint(39.902909, 116.413907, 10000.0))
    }

    fun initPoint() {
        clusterOverlay.clearClusterItem()
        val lat = 39.902909
        val lng = 116.413907
        for (i in 0..10000) {
            val point =
                GPSUtil.UtilLatLng(lat + Random.nextDouble(0.01), lng + Random.nextDouble(0.01))
            val d = D(i, i.toString())
            clusterOverlay.addClusterItem(ClusterItem(point, d))
        }
    }

    override fun onPause() {
        mapView.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        mapView.resume()
    }

    override fun onDestroy() {
        mapView.dispose()
        super.onDestroy()
    }

    /**
     * 地图点击
     */
    open fun onMapClick(point: Point) {}

    /**
     * 标记点击
     */
    fun onMarkerClick(marker: Graphic) {
        if (marker.attributes.containsKey("NAME")) {
            val name = marker.attributes["NAME"] as String
            val str = marker.attributes["OBJ"] as String
            when (name) {

                Cluster::class.java.name -> {
                    //聚合点
                    val type = marker.attributes["TYPE"] as String
                    when (type) {
                        D::class.java.name -> {
                            val obj = mJsonUtil.getObjectByStr<Cluster<D>>(
                                str,
                                object : TypeToken<Cluster<D>>() {}.type
                            )
                            obj?.let {
                                if (it.clusterItems.size > 1) {
                                    val ids = mutableListOf<Int>()
                                    val names = mutableListOf<String>()
                                    for (clusterItem in it.clusterItems) {
                                        ids.add(clusterItem.obj.id)
                                        names.add(clusterItem.obj.str)
                                    }
                                    AlertDialog.Builder(this)
                                        .setTitle("请选择")
                                        .setItems(names.toTypedArray()) { _, which ->
                                            Toast.makeText(this, names[which], Toast.LENGTH_SHORT)
                                                .show()
                                        }
                                        .setNegativeButton("关闭", null)
                                        .show()
                                } else {
                                    val item = it.clusterItems.first().obj
                                    Toast.makeText(this, item.str, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    /**
     * 屏幕移动
     */
    open fun onNavigationChanged(event: NavigationChangedEvent?) {}

    /**
     * 镜头移动
     */
    open fun viewpointChanged(event: ViewpointChangedEvent?) {}

    /**
     * 位置移动
     */
    open fun onLocationChanged(event: LocationDisplay.LocationChangedEvent?) {}

    /**
     * 地图缩放
     */
    open fun onMapScaleChanged(event: MapScaleChangedEvent?) {}


}