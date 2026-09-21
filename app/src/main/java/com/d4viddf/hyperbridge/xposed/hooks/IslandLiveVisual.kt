package com.d4viddf.hyperbridge.xposed.hooks

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroupOverlay
import android.view.animation.PathInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * In-place island visuals Xiaomi does not animate on owned HyperBridge proxies.
 *
 * Subtitle overlay views are disabled: HyperOS FocusPlugin checkError deletes the
 * island if extra views appear during updateBigIslandView. Progress bars still
 * interpolate on the existing widgets.
 */
internal object IslandLiveVisual {
    private const val DURATION_MS = 380L
    private const val OVERLAY_TAG = 0x48B71E51
    private val interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
    private val running = WeakHashMap<View, Animator>()

    data class Snapshot(
        val texts: Map<TextView, String>,
        val progress: Map<View, Int>,
    )

    fun capture(island: ViewGroup): Snapshot {
        val progress = LinkedHashMap<View, Int>()
        runCatching { collectProgress(island, progress) }
        return Snapshot(emptyMap(), progress)
    }

    fun play(island: ViewGroup, before: Snapshot?, onFinished: () -> Unit) {
        if (before == null) {
            onFinished()
            return
        }
        try {
            val afterProgress = LinkedHashMap<View, Int>()
            collectProgress(island, afterProgress)

            var pending = 1
            val done = {
                pending--
                if (pending <= 0) onFinished()
            }
            afterProgress.forEach { (view, newValue) ->
                val oldValue = before.progress[view] ?: return@forEach
                if (oldValue == newValue) return@forEach
                pending++
                animateProgress(view, oldValue, newValue, done)
            }
            done()
        } catch (_: Throwable) {
            restoreVisible(island)
            onFinished()
        }
    }

    private fun collectProgress(view: View, progress: MutableMap<View, Int>) {
        if (view.getTag(OVERLAY_TAG) == true) return
        progressOf(view)?.let { progress[view] = it }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) collectProgress(view.getChildAt(index), progress)
        }
    }

    private fun restoreVisible(root: View) {
        if (root is TextView) {
            root.alpha = 1f
            root.translationY = 0f
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) restoreVisible(root.getChildAt(index))
        }
    }

    private fun isTimerLike(value: String): Boolean =
        value.matches(Regex("""^\d{1,2}:\d{2}(?::\d{2})?$"""))

    private fun shouldAnimateText(view: TextView, oldText: String, newText: String): Boolean {
        val old = oldText.trim()
        val new = newText.trim()
        if (old.isEmpty() || new.isEmpty() || old == new) return false
        if (!view.isAttachedToWindow || view.height <= 0 || view.width <= 0) return false
        return view.parent is ViewGroup
    }

    private fun animateSubtitle(view: TextView, oldText: String, newText: String, onEnd: () -> Unit) {
        running.remove(view)?.cancel()
        val parent = view.parent as? ViewGroup
        val overlayHost = parent?.overlay
        if (parent == null || overlayHost !is ViewGroupOverlay || !view.isAttachedToWindow) {
            view.alpha = 1f
            view.translationY = 0f
            view.text = newText
            onEnd()
            return
        }
        val shift = view.height.coerceAtLeast(view.lineHeight).toFloat()
        val overlay = TextView(view.context).apply {
            setTag(OVERLAY_TAG, true)
            text = oldText
            setTextColor(view.currentTextColor)
            textSize = view.textSize / resources.displayMetrics.scaledDensity
            typeface = view.typeface
            gravity = view.gravity
            maxLines = 1
            ellipsize = view.ellipsize
            letterSpacing = view.letterSpacing
            setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)
            alpha = 1f
            translationY = 0f
        }
        try {
            overlay.measure(
                View.MeasureSpec.makeMeasureSpec(view.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(view.height, View.MeasureSpec.EXACTLY),
            )
            overlay.layout(view.left, view.top, view.right, view.bottom)
            overlayHost.add(overlay)
        } catch (_: Throwable) {
            view.alpha = 1f
            view.translationY = 0f
            view.text = newText
            onEnd()
            return
        }
        view.text = newText
        view.alpha = 0f
        view.translationY = shift

        val oldMove = ObjectAnimator.ofFloat(overlay, View.TRANSLATION_Y, 0f, -shift)
        val oldFade = ObjectAnimator.ofFloat(overlay, View.ALPHA, 1f, 0f)
        val newMove = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, shift, 0f)
        val newFade = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f)
        val set = AnimatorSet().apply {
            playTogether(oldMove, oldFade, newMove, newFade)
            duration = DURATION_MS
            interpolator = IslandLiveVisual.interpolator
        }
        val viewRef = WeakReference(view)
        val overlayRef = WeakReference(overlay)
        val hostRef = WeakReference(overlayHost)
        set.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                finish(cancelled = false)
            }

            override fun onAnimationCancel(animation: Animator) {
                finish(cancelled = true)
            }

            private fun finish(cancelled: Boolean) {
                overlayRef.get()?.let { hostRef.get()?.remove(it) }
                viewRef.get()?.let {
                    it.alpha = 1f
                    it.translationY = 0f
                    if (cancelled) it.text = newText
                    running.remove(it)
                }
                onEnd()
            }
        })
        running[view] = set
        try {
            set.start()
        } catch (_: Throwable) {
            overlayHost.remove(overlay)
            view.alpha = 1f
            view.translationY = 0f
            view.text = newText
            running.remove(view)
            onEnd()
        }
    }

    private fun animateProgress(view: View, from: Int, to: Int, onEnd: () -> Unit) {
        running.remove(view)?.cancel()
        try {
            if (view is ProgressBar) {
                view.progress = from
                view.setProgress(to, true)
                view.postDelayed({ onEnd() }, DURATION_MS)
                return
            }
            writeProgress(view, from)
            val animator = ObjectAnimator.ofInt(from, to).apply {
                duration = DURATION_MS
                interpolator = IslandLiveVisual.interpolator
                addUpdateListener { writeProgress(view, it.animatedValue as Int) }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        running.remove(view)
                        onEnd()
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        writeProgress(view, to)
                        running.remove(view)
                        onEnd()
                    }
                })
            }
            running[view] = animator
            animator.start()
        } catch (_: Throwable) {
            writeProgress(view, to)
            running.remove(view)
            onEnd()
        }
    }

    private fun progressOf(view: View): Int? {
        if (view is ProgressBar) return view.progress
        val name = view.javaClass.simpleName
        if (!name.contains("Progress", ignoreCase = true) && !name.contains("Arc", ignoreCase = true)) {
            return null
        }
        val value = IslandHookReflection.invokeNoArg(view, "getProgress") ?: return null
        return when (value) {
            is Int -> value
            is Float -> if (value <= 1f) (value * 100f).toInt() else value.toInt()
            is Double -> if (value <= 1.0) (value * 100.0).toInt() else value.toInt()
            else -> null
        }
    }

    private fun writeProgress(view: View, value: Int) {
        if (view is ProgressBar) {
            view.progress = value
            return
        }
        if (IslandHookReflection.invokeIntSetter(view, "setProgress", value)) return
        IslandHookReflection.invokeFloatSetter(view, "setProgress", value.toFloat())
    }
}
