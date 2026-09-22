package com.lidesheng.hyperlyric.root.island.host

import android.view.ViewGroup

/**
 * Reflection-only helpers shared by the thin SystemUI lifecycle adapters.
 *
 * Presentation policy and view mutations live in
 * [IslandPresentationCoordinator] and [IslandInjectionReconciler].
 */
internal object IslandTextHookerSupport {
    const val TAG = "IslandTextHooker"

    fun extractIslandDataFromContentOrReal(contentView: ViewGroup): Any? {
        val currentData = IslandProbeUtils.getCurrentIslandData(contentView)
        val realView = callNoArgMethodResult(contentView, "getRealView")
        val realData = IslandProbeUtils.getCurrentIslandData(realView)

        return when {
            IslandProbeUtils.extractMediaIslandInfo(currentData) != null -> currentData
            IslandProbeUtils.extractMediaIslandInfo(realData) != null -> realData
            currentData != null && IslandProbeUtils.isMediaIsland(currentData) -> currentData
            realData != null && IslandProbeUtils.isMediaIsland(realData) -> realData
            else -> currentData ?: realData
        }
    }

    fun callNoArgMethodResult(receiver: Any, name: String): Any? {
        return runCatching {
            receiver.javaClass.methods.find {
                it.name == name && it.parameterTypes.isEmpty()
            }?.invoke(receiver)
        }.getOrNull()
    }

    fun findFieldValue(receiver: Any?, name: String): Any? {
        val target = receiver ?: return null
        return runCatching {
            var current: Class<*>? = target.javaClass
            var value: Any? = null
            while (current != null && value == null) {
                val field = current.declaredFields.find { it.name == name }
                if (field != null) {
                    field.isAccessible = true
                    value = field.get(target)
                }
                current = current.superclass
            }
            value
        }.getOrNull()
    }
}
