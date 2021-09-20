package com.example.arcgisandroidcluster

import android.content.res.Resources
import android.util.TypedValue

const val LOCATION_FORMAT = "%.6f"

/**
 * 坐标格式化限制长度
 */
fun Double.latlngFormat(): String {
    return String.format(LOCATION_FORMAT, this)
}

/**
 * dp2px
 */
val Float.dp2px get() = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this, Resources.getSystem().displayMetrics).toInt()

/**
 * sp2px
 */
val Float.sp2px get() = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, this, Resources.getSystem().displayMetrics).toInt()
