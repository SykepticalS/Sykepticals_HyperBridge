package com.sykeptical.hyperpop.xposed.hooks

import android.graphics.BlendMode
import android.graphics.LinearGradient
import android.graphics.RenderEffect
import android.graphics.Shader
import android.view.View
import android.view.ViewGroup
import com.sykeptical.hyperpop.service.animation.expanded.FlowMask
import java.util.Collections
import java.util.WeakHashMap

/**
 * The current takeover's Ambient Flow fade, in window coordinates.
 * Amount 0 leaves every flow renderer on its native colors.
 */
object ExpandedSurfaceState {
    data class Mask(
        val blackUntilY: Int,
        val fadeEndY: Int,
        val amount: Float,
    )

    @Volatile var mask: Mask? = null
}

/**
 * Fades native and static media backgrounds into the black island fill.
 * HyperPop's own flow shader reads [ExpandedSurfaceState] and is left alone.
 */
object ExpandedFlowMaskApplicator {
    private const val FLOW_TAG = "hyperpop.media.island_expanded_media_custom_flow"
    private const val HOLDER_BACKGROUND_TAG = "hyperpop.media.island_media_holder_background"
    private val masked = Collections.synchronizedMap(WeakHashMap<View, Boolean>())

    fun apply(root: View, flow: FlowMask?, amount: Float) {
        if (flow == null || amount <= 0.01f) {
            clear()
            return
        }
        ExpandedSurfaceState.mask = ExpandedSurfaceState.Mask(flow.blackUntilY, flow.fadeEndY, amount)
        val seen = HashSet<View>()
        walk(root, flow, 0, seen)
        val stale = masked.keys.filter { it !in seen }
        stale.forEach { view ->
            view.setRenderEffect(null)
            masked.remove(view)
        }
    }

    fun clear() {
        ExpandedSurfaceState.mask = null
        masked.keys.toList().forEach { view -> view.setRenderEffect(null) }
        masked.clear()
    }

    private fun walk(view: View, flow: FlowMask, depth: Int, seen: MutableSet<View>) {
        if (depth > 8) return
        if (isMaskTarget(view)) {
            view.setRenderEffect(effect(view, flow))
            masked[view] = true
            seen += view
        }
        val group = view as? ViewGroup ?: return
        for (index in 0 until group.childCount) walk(group.getChildAt(index), flow, depth + 1, seen)
    }

    private fun isMaskTarget(view: View): Boolean {
        val tag = view.tag as? String
        if (tag == FLOW_TAG) return false
        if (view.javaClass.name.contains("MediaFlowBackgroundView")) return false
        if (tag == HOLDER_BACKGROUND_TAG) return true
        return view.javaClass.name.contains("MusicBgView")
    }

    private fun effect(view: View, flow: FlowMask): RenderEffect {
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val start = (flow.blackUntilY - location[1]).toFloat()
        val end = (flow.fadeEndY - location[1]).toFloat()
        val shader = LinearGradient(
            0f,
            start,
            0f,
            end,
            intArrayOf(0x00000000, 0xFF000000.toInt()),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        return RenderEffect.createBlendModeEffect(
            RenderEffect.createOffsetEffect(0f, 0f),
            RenderEffect.createShaderEffect(shader),
            BlendMode.DST_IN,
        )
    }
}
