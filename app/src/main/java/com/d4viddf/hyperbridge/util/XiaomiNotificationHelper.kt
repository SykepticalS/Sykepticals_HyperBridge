package com.d4viddf.hyperbridge.util

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.lang.reflect.Method

object XiaomiNotificationHelper {

    /**
     * Checks if the device's OS supports the Xiaomi Island feature.
     */
    fun isSupportIsland(): Boolean {
        return getSystemPropertyBoolean("persist.sys.feature.island", false)
    }

    /**
     * Returns the focus protocol version:
     * 1: OS1 (OS1 focus notification templates)
     * 2: OS2 (OS2 focus notification templates)
     * 3: OS3 (OS3 Hyper Island notification templates)
     * 0: Not supported or unknown
     */
    fun getFocusProtocolVersion(context: Context): Int {
        return try {
            Settings.System.getInt(
                context.contentResolver,
                "notification_focus_protocol", 0
            )
        } catch (e: Exception) {
            0
        }
    }

    @SuppressLint("PrivateApi")
    private fun getSystemPropertyBoolean(key: String, defaultValue: Boolean): Boolean {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method: Method = clazz.getDeclaredMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
            val result = method.invoke(null, key, false)
            result as? Boolean ?: defaultValue
        } catch (_: Exception) {
            defaultValue
        }
    }
}
