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
    private val effects = Collections.synchronizedMap(WeakHashMap<View, Applied>())
    private var targetsRoot = java.lang.ref.WeakReference<View>(null)
    private var targets = emptyList<java.lang.ref.WeakReference<View>>()

    fun apply(root: View, flow: FlowMask?, amount: Float) {
        if (flow == null || amount <= 0.01f) {
            clear()
            return
        }
        val current = ExpandedSurfaceState.mask
        if (current == null || current.blackUntilY != flow.blackUntilY ||
            current.fadeEndY != flow.fadeEndY || current.amount != amount
        ) {
            ExpandedSurfaceState.mask = ExpandedSurfaceState.Mask(flow.blackUntilY, flow.fadeEndY, amount)
        }
        if (targetsRoot.get() !== root) {
            val found = ArrayList<View>(4)
            walk(root, 0, found)
            targets = found.map { java.lang.ref.WeakReference(it) }
            targetsRoot = java.lang.ref.WeakReference(root)
            val live = found.toSet()
            effects.keys.filter { it !in live }.forEach { view ->
                view.setRenderEffect(null)
                effects.remove(view)
            }
        }
        val location = IntArray(2)
        for (reference in targets) {
            val view = reference.get() ?: continue
            view.getLocationInWindow(location)
            val cached = effects[view]
            if (cached != null && cached.blackUntilY == flow.blackUntilY &&
                cached.fadeEndY == flow.fadeEndY && cached.locationY == location[1]
            ) {
                continue
            }
            val effect = effect(location[1], flow)
            view.setRenderEffect(effect)
            effects[view] = Applied(flow.blackUntilY, flow.fadeEndY, location[1], effect)
        }
    }

    /**
     * Masks the flow views under [root] with a fade measured from [cardTopInWindow].
     * The effects are view-local, so they move with a card that is dragged.
     * Independent of the real island's targets; returns the masked views.
     */
    fun applyFromCardTop(root: View, relative: FlowMask, cardTopInWindow: Int): List<View> {
        val found = ArrayList<View>(4)
        walk(root, 0, found)
        val location = IntArray(2)
        found.forEach { view ->
            view.getLocationInWindow(location)
            val local = FlowMask(
                cardTopInWindow + relative.blackUntilY,
                cardTopInWindow + relative.fadeEndY,
            )
            view.setRenderEffect(effect(location[1], local))
        }
        return found
    }

    fun invalidateStructure() {
        targetsRoot = java.lang.ref.WeakReference(null)
    }

    fun clear() {
        ExpandedSurfaceState.mask = null
        effects.keys.toList().forEach { view -> view.setRenderEffect(null) }
        effects.clear()
        targets = emptyList()
        targetsRoot = java.lang.ref.WeakReference(null)
    }

    private class Applied(
        val blackUntilY: Int,
        val fadeEndY: Int,
        val locationY: Int,
        val effect: RenderEffect,
    )

    private fun walk(view: View, depth: Int, found: MutableList<View>) {
        if (depth > 8) return
        if (isMaskTarget(view)) found += view
        val group = view as? ViewGroup ?: return
        for (index in 0 until group.childCount) walk(group.getChildAt(index), depth + 1, found)
    }

    private fun isMaskTarget(view: View): Boolean {
        val tag = view.tag as? String
        if (tag == FLOW_TAG) return false
        if (view.javaClass.name.contains("MediaFlowBackgroundView")) return false
        if (tag == HOLDER_BACKGROUND_TAG) return true
        return view.javaClass.name.contains("MusicBgView")
    }

    private fun effect(locationY: Int, flow: FlowMask): RenderEffect {
        val start = (flow.blackUntilY - locationY).toFloat()
        val end = (flow.fadeEndY - locationY).toFloat()
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
