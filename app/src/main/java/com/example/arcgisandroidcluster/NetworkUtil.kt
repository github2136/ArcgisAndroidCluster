package com.example.arcgisandroidcluster

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.os.Build
import java.net.InetSocketAddress
import java.net.Socket

class NetworkUtil private constructor(context: Context) {
    private val connectivityManager: ConnectivityManager by lazy { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }

    //是否有网络
    fun isNetworkAvailable(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ?: false
        } else {
            isLinkable(connectivityManager.activeNetworkInfo)
        }
    }


    //是否连接wifi
    fun isWifiAvailable(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
        } else {
            isLinkable(connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI))
        }
    }

    //是否连接手机网络
    fun isMobileAvailable(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ?: false
        } else {
            isLinkable(connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE))
        }

    }

    //指定ip端口能否连接
    fun isHostConnection(host: String, port: Int, timeout: Int = 3000): Boolean {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(host, port), timeout)
        } catch (e: Exception) {
            return false
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
            }
        }
        return true
    }

    //判断是否是内网地址
    fun isInnerIP(ipAddress: String): Boolean {
        val isInnerIp: Boolean
        val ipNum = getIpNum(ipAddress)
        /**
         * 私有IP：A类  10.0.0.0-10.255.255.255
         * B类  172.16.0.0-172.31.255.255
         * C类  192.168.0.0-192.168.255.255
         * 当然，还有127这个网段是环回地址
         */
        val aBegin = getIpNum("10.0.0.0")
        val aEnd = getIpNum("10.255.255.255")
        val bBegin = getIpNum("172.16.0.0")
        val bEnd = getIpNum("172.31.255.255")
        val cBegin = getIpNum("192.168.0.0")
        val cEnd = getIpNum("192.168.255.255")
        isInnerIp = isInner(ipNum, aBegin, aEnd) || isInner(ipNum, bBegin, bEnd) || isInner(ipNum, cBegin, cEnd) || ipAddress == "127.0.0.1"
        return isInnerIp
    }

    //ip转数字
    private fun getIpNum(ipAddress: String): Long {
        val ip = ipAddress.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val a = Integer.parseInt(ip[0]).toLong()
        val b = Integer.parseInt(ip[1]).toLong()
        val c = Integer.parseInt(ip[2]).toLong()
        val d = Integer.parseInt(ip[3]).toLong()

        return a * 256 * 256 * 256 + b * 256 * 256 + c * 256 + d
    }

    private fun isInner(userIp: Long, begin: Long, end: Long): Boolean {
        return userIp in begin..end
    }

    private fun isLinkable(info: NetworkInfo?): Boolean {
        return info?.isConnected ?: false
    }

    companion object {

        @Volatile
        private var instance: NetworkUtil? = null

        fun getInstance(context: Context): NetworkUtil {
            if (instance == null) {
                synchronized(NetworkUtil::class) {
                    if (instance == null) {
                        instance = NetworkUtil(context)
                    }
                }
            }
            return instance!!
        }
    }
}