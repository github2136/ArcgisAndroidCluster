package com.example.arcgisandroidcluster

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import java.lang.reflect.Type

class JsonUtil private constructor() {
    private val mGson: Gson = Companion.mGson
    fun getGson(): Gson {
        return mGson
    }

    @Synchronized
    fun <T> getObjectByStr(json: String?, cls: Class<T>): T? {
        return try {
            mGson.fromJson<T>(json, cls)
        } catch (e: JsonSyntaxException) {
            null
        }
    }

    @Synchronized
    fun <T> getObjectByStr(json: String?, typeOf: Type): T? {
        return try {
            mGson.fromJson<T>(json, typeOf)
        } catch (e: JsonSyntaxException) {
            null
        }
    }

    companion object {
        var dateFormat = "yyyy-MM-dd HH:mm:ss"
        var mGson = GsonBuilder().setDateFormat(dateFormat).create()
        val instance: JsonUtil by lazy {
            JsonUtil()
        }

        fun newInstance(): JsonUtil {
            return JsonUtil()
        }
    }
}