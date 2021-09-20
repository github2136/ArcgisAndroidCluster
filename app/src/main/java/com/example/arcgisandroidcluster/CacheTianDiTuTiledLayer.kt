package com.example.arcgisandroidcluster

import android.content.Context
import com.esri.arcgisruntime.arcgisservices.LevelOfDetail
import com.esri.arcgisruntime.arcgisservices.TileInfo
import com.esri.arcgisruntime.data.TileKey
import com.esri.arcgisruntime.geometry.Envelope
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.SpatialReference
import com.esri.arcgisruntime.layers.ImageTiledLayer
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

class CacheTianDiTuTiledLayer(context: Context, val subDomain: String, val type: String, val tilematrixset: String, tileInfo: TileInfo, envelope: Envelope) : ImageTiledLayer(tileInfo, envelope) {
    //缓存路径
    private var CACHE_PATH: String = context.getExternalFilesDir("MapCache/TainDiTu/${type}_${tilematrixset}")!!.toString()
    private val networkUtil by lazy { NetworkUtil.getInstance(context) }

    init {
        val file = File(CACHE_PATH)
        if (!file.exists()) { //判断文件目录是否存在
            file.mkdirs()
        }
    }

    override fun getTile(tileKey: TileKey): ByteArray? {
        val level = tileKey.level
        val col = tileKey.column
        val row = tileKey.row
        // 若缓存存在则返回缓存字节
        val fileName = "${col}_${row}.png"
        if (hasCached(level.toString() + "", fileName)) {
            //本地存在
            val _cacheLevelDir = File("$CACHE_PATH/$level")
            var cachedBytes: ByteArray?
            for (_file in _cacheLevelDir.listFiles()) {
                if (_file.name == fileName) {
                    cachedBytes = ByteArray(_file.length().toInt())
                    try {
                        val _fis = FileInputStream(_file)
                        _fis.read(cachedBytes)
                        _fis.close()
                        return cachedBytes
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }
        //本地不存在，从网络加载
        return if (networkUtil.isNetworkAvailable()) downloadData(level, row, col) else null
    }

    /**
     * 判断是否有缓存数据
     *
     * @param level    级别
     * @param fileName 瓦片名称  row_col.png
     * @return
     */
    private fun hasCached(level: String, fileName: String): Boolean {
        val _cacheLevelDir = File("$CACHE_PATH/$level")
        if (!_cacheLevelDir.exists()) {
            _cacheLevelDir.mkdir()
            return false
        }
        for (_file in _cacheLevelDir.listFiles()) {
            if (_file.name == fileName) { //存在同名文件
                return try {
                    val cachedBytes = ByteArray(_file.length().toInt())
                    val _fis = FileInputStream(_file)
                    _fis.read(cachedBytes) //文件可读
                    _fis.close()
                    true
                } catch (e: Exception) {
                    //文件不可读
                    false
                }
            }
        }
        //不存在同名文件
        return false
    }

    /**
     * 从网络加载瓦片，并保存本地
     *
     * @param level 级别
     * @param row   行
     * @param col   列
     * @return 瓦片数据
     */
    private fun downloadData(level: Int, row: Int, col: Int): ByteArray? {
        var result: ByteArray? = null
        try {
            val url = URL("http://${subDomain}.tianditu.gov.cn/" +
                              "${type}_${tilematrixset}/wmts?" +
                              "service=wmts" +
                              "&request=gettile" +
                              "&version=1.0.0" +
                              "&layer=${type}" +
                              "&format=tiles" +
                              "&STYLE=default" +
                              "&tilematrixset=${tilematrixset}" +
                              "&tilecol=${col}" +
                              "&tilerow=${row}" +
                              "&tilematrix=${level}" +
                              "&tk=${key}"
            )
            val buf = ByteArray(1024)
            val httpConnection: HttpURLConnection = url.openConnection() as HttpURLConnection
            httpConnection.connect()
            val `is` = BufferedInputStream(httpConnection.inputStream)
            val bos = ByteArrayOutputStream()
            var temp: Int
            while (`is`.read(buf).also { temp = it } > 0) {
                bos.write(buf, 0, temp)
            }
            `is`.close()
            httpConnection.disconnect()
            result = bos.toByteArray()
            saveMapCache(result, "${col}_${row}.png", "$level")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    /**
     * 保存瓦片本地
     *
     * @param bytes    瓦片数据
     * @param fileName 瓦片命名
     * @param level    级别
     */
    private fun saveMapCache(bytes: ByteArray, fileName: String, level: String) {
        try {
            val _cacheDir = File(CACHE_PATH)
            if (!_cacheDir.exists()) {
                _cacheDir.mkdir()
            }
            val _cacheLevelDir = File("$CACHE_PATH/$level")
            if (!_cacheLevelDir.exists()) {
                _cacheLevelDir.mkdir()
            }
            val _cacheFile = File(_cacheLevelDir, fileName)
            _cacheFile.createNewFile()
            val _fos = FileOutputStream(_cacheFile)
            _fos.write(bytes)
            _fos.close()
        } catch (e: IOException) {

        }
    }

    companion object {
        private const val key ="dd8fc4bb72a30c015bb12b65ee2778c8"
        private val SubDomain = mutableListOf("t0", "t1", "t2", "t3", "t4", "t5", "t6", "t7")
        private const val DPI = 96
        private const val minZoomLevel = 1
        private const val maxZoomLevel = 18
        private const val tileWidth = 256
        private const val tileHeight = 256
        private val SRID_MERCATOR = SpatialReference.create(102100)
        private const val X_MIN_MERCATOR = -20037508.3427892
        private const val Y_MIN_MERCATOR = -20037508.3427892
        private const val X_MAX_MERCATOR = 20037508.3427892
        private const val Y_MAX_MERCATOR = 20037508.3427892
        private val ORIGIN_MERCATOR = Point(-20037508.3427892, 20037508.3427892, SRID_MERCATOR)
        private val ENVELOPE_MERCATOR = Envelope(X_MIN_MERCATOR, Y_MIN_MERCATOR, X_MAX_MERCATOR, Y_MAX_MERCATOR, SRID_MERCATOR)
        private val SRID_2000 = SpatialReference.create(4490)
        private const val X_MIN_2000 = -180.0
        private const val Y_MIN_2000 = -90.0
        private const val X_MAX_2000 = 180.0
        private const val Y_MAX_2000 = 90.0
        private val ORIGIN_2000: Point = Point(-180.0, 90.0, SRID_2000)
        private val ENVELOPE_2000 = Envelope(X_MIN_2000, Y_MIN_2000, X_MAX_2000, Y_MAX_2000, SRID_2000)
        private val SCALES = doubleArrayOf(
            2.958293554545656E8, 1.479146777272828E8,
            7.39573388636414E7, 3.69786694318207E7,
            1.848933471591035E7, 9244667.357955175,
            4622333.678977588, 2311166.839488794,
            1155583.419744397, 577791.7098721985,
            288895.85493609926, 144447.92746804963,
            72223.96373402482, 36111.98186701241,
            18055.990933506204, 9027.995466753102,
            4513.997733376551, 2256.998866688275,
            1128.4994333441375
        )
        private val RESOLUTIONS_MERCATOR = doubleArrayOf(
            78271.51696402048, 39135.75848201024,
            19567.87924100512, 9783.93962050256,
            4891.96981025128, 2445.98490512564,
            1222.99245256282, 611.49622628141,
            305.748113140705, 152.8740565703525,
            76.43702828517625, 38.21851414258813,
            19.109257071294063, 9.554628535647032,
            4.777314267823516, 2.388657133911758,
            1.194328566955879, 0.5971642834779395,
            0.298582141738970
        )
        private val RESOLUTIONS_2000 = doubleArrayOf(
            0.7031249999891485, 0.35156249999999994,
            0.17578124999999997, 0.08789062500000014,
            0.04394531250000007, 0.021972656250000007,
            0.01098632812500002, 0.00549316406250001,
            0.0027465820312500017, 0.0013732910156250009,
            0.000686645507812499, 0.0003433227539062495,
            0.00017166137695312503, 0.00008583068847656251,
            0.000042915344238281406, 0.000021457672119140645,
            0.000010728836059570307, 0.000005364418029785169
        )


        fun get(context: Context, layerType: LayerType): CacheTianDiTuTiledLayer {
            var type = ""
            var tilematrixset = ""
            val mainTileInfo: TileInfo
            val mainEnvelope: Envelope
            when (layerType) {
                LayerType.TIANDITU_VECTOR_MERCATOR -> {
                    type = "vec"
                    tilematrixset = "w"
                }
                LayerType.TIANDITU_VECTOR_MERCATOR_LABLE -> {
                    type = "cva"
                    tilematrixset = "w"
                }
                LayerType.TIANDITU_VECTOR_2000 -> {
                    type = "vec"
                    tilematrixset = "c"
                }
                LayerType.TIANDITU_VECTOR_2000_LABLE -> {
                    type = "cva"
                    tilematrixset = "c"
                }
                LayerType.TIANDITU_IMAGE_MERCATOR -> {
                    type = "img"
                    tilematrixset = "w"
                }
                LayerType.TIANDITU_IMAGE_MERCATOR_LABLE -> {
                    type = "cia"
                    tilematrixset = "w"
                }
                LayerType.TIANDITU_IMAGE_2000 -> {
                    type = "img"
                    tilematrixset = "c"
                }
                LayerType.TIANDITU_IMAGE_2000_LABLE -> {
                    type = "cia"
                    tilematrixset = "c"
                }
                LayerType.TIANDITU_TERRAIN_MERCATOR -> {
                    type = "ter"
                    tilematrixset = "w"
                }
                LayerType.TIANDITU_TERRAIN_MERCATOR_LABLE -> {
                    type = "cta"
                    tilematrixset = "w"
                }
                LayerType.TIANDITU_TERRAIN_2000 -> {
                    type = "ter"
                    tilematrixset = "c"
                }
                LayerType.TIANDITU_TERRAIN_2000_LABLE -> {
                    type = "cta"
                    tilematrixset = "c"
                }
            }
            val mainLevelOfDetail: MutableList<LevelOfDetail> = ArrayList()
            val mainOrigin: Point
            if (tilematrixset == "c") {
                for (i in minZoomLevel..maxZoomLevel) {
                    val item = LevelOfDetail(
                        i,
                        RESOLUTIONS_2000[i - 1],
                        SCALES[i - 1]
                    )
                    mainLevelOfDetail.add(item)
                }
                mainEnvelope = ENVELOPE_2000
                mainOrigin = ORIGIN_2000
            } else {
                for (i in minZoomLevel..maxZoomLevel) {
                    val item = LevelOfDetail(
                        i,
                        RESOLUTIONS_MERCATOR[i - 1],
                        SCALES[i - 1]
                    )
                    mainLevelOfDetail.add(item)
                }
                mainEnvelope = ENVELOPE_MERCATOR
                mainOrigin = ORIGIN_MERCATOR
            }
            mainTileInfo = TileInfo(DPI, TileInfo.ImageFormat.PNG24, mainLevelOfDetail, mainOrigin, mainOrigin.spatialReference, tileHeight, tileWidth)
            return CacheTianDiTuTiledLayer(context, SubDomain.random(), type, tilematrixset, mainTileInfo, mainEnvelope)
        }
    }


    enum class LayerType {
        /**
         * 天地图矢量墨卡托投影地图服务
         */
        TIANDITU_VECTOR_MERCATOR, TIANDITU_VECTOR_MERCATOR_LABLE,

        /**
         * 天地图矢量2000地图服务
         */
        TIANDITU_VECTOR_2000, TIANDITU_VECTOR_2000_LABLE,

        /**
         * 天地图影像墨卡托地图服务
         */
        TIANDITU_IMAGE_MERCATOR, TIANDITU_IMAGE_MERCATOR_LABLE,

        /**
         * 天地图影像2000地图服务
         */
        TIANDITU_IMAGE_2000, TIANDITU_IMAGE_2000_LABLE,

        /**
         * 天地图地形墨卡托地图服务
         */
        TIANDITU_TERRAIN_MERCATOR, TIANDITU_TERRAIN_MERCATOR_LABLE,

        /**
         * 天地图地形2000地图服务
         */
        TIANDITU_TERRAIN_2000, TIANDITU_TERRAIN_2000_LABLE
    }
}