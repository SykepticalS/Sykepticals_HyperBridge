package com.d4viddf.hyperbridge.xposed.mediacard.island.compact

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.graphics.RenderEffect
import android.graphics.Shader
import android.view.Choreographer
import android.view.View
import com.d4viddf.hyperbridge.models.MarqueeMotion
import com.d4viddf.hyperbridge.xposed.mediacard.island.compact.CompactMediaIslandPolicy.CyclePhase
import java.lang.ref.WeakReference

/**
 * Drives one injected compact-media title.
 *
 * Cycle mode shows the title, then "By: artist", then settles on the title. Each cycle line
 * scrolls once, waits a second at the end and turns to the next line with Xiaomi's text
 * switch motion. The settled title then follows the user's scroll mode. Time only runs while
 * the island is on screen, so a track changed from the expanded island or the shade still
 * plays its cycle once the compact island is visible again.
 */
internal class CompactMediaTitleController(
    primary: CompactTitleView,
    ghost: CompactTitleView,
    /** Xiaomi's transition copy: shows the real island's current line at rest and never scrolls. */
    private val passive: Boolean,
    /** False while the island is inside an app, expanded, or animating between states. */
    private val islandAtRest: (View) -> Boolean,
    /** Re-measures the default-length island whenever the displayed line changes. */
    private val onLineChanged: (String) -> Unit,
) : Choreographer.FrameCallback {
    private val primaryRef = WeakReference(primary)
    private val ghostRef = WeakReference(ghost)
    private val choreographer = Choreographer.getInstance()
    private var running = false
    private var identity: String? = null
    private var configKey = ""
    private var settings = CompactMediaIslandSettings.defaults()
    private var title = ""
    private var artistLine = ""
    private var phase = CyclePhase.SETTLED
    private var state = State.IDLE
    private var stateStartNanos = 0L
    private var paused = false
    private var spotKey = Long.MIN_VALUE
    private var spotSinceNanos = 0L
    private val spot = IntArray(2)
    private var lastX = 0
    private var lastY = 0
    private var lastScale = 1f
    private var completedLoops = 0
    private var overflow = 0f
    private var speed = 0f
    private var turn: ValueAnimator? = null

    fun render(settings: CompactMediaIslandSettings, title: String, artist: String, identity: String) {
        val view = primaryRef.get() ?: return
        if (passive) {
            view.text = sharedLine?.takeIf { it.first == identity }?.second ?: title
            view.offset = 0f
            onLineChanged(view.text)
            return
        }
        val key = listOf(
            settings.cycleActive,
            settings.titleScrollMode,
            settings.titleScrollSpeed,
            settings.titleScrollBounce,
        ).joinToString("|")
        this.settings = settings
        this.title = title
        this.artistLine = CompactMediaIslandPolicy.artistLine(artist)
        val trackChanged = identity != this.identity
        if (!trackChanged && key == configKey) {
            resume()
            return
        }
        val hadText = this.identity != null && view.text.isNotEmpty()
        this.identity = identity
        configKey = key
        phase = if (settings.cycleActive && settledIdentity != identity) CyclePhase.TITLE else CyclePhase.SETTLED
        val next = CompactMediaIslandPolicy.cycleText(phase, title, artistLine)
        val animate = trackChanged && hadText && view.text != next && isOnScreen(view)
        beginLine(view, next, animate)
        start()
    }

    fun stop() {
        running = false
        choreographer.removeFrameCallback(this)
        turn?.cancel()
        primaryRef.get()?.offset = 0f
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val view = primaryRef.get()
        if (view == null || !view.isAttachedToWindow) {
            running = false
            return
        }
        if (!isOnScreen(view)) {
            pause(view)
            spotKey = Long.MIN_VALUE
            choreographer.postFrameCallbackDelayed(this, HIDDEN_POLL_MS)
            return
        }
        if (!islandAtRest(view)) {
            pause(view)
            spotKey = Long.MIN_VALUE
            choreographer.postFrameCallbackDelayed(this, BUSY_POLL_MS)
            return
        }
        if (!isSettled(view, frameTimeNanos)) {
            pause(view)
            choreographer.postFrameCallback(this)
            return
        }
        if (paused) {
            paused = false
            stateStartNanos = 0L
        }
        if (stateStartNanos == 0L) stateStartNanos = frameTimeNanos
        step(view, frameTimeNanos)
        if (running) choreographer.postFrameCallback(this)
    }

    private fun step(view: CompactTitleView, now: Long) {
        val elapsed = (now - stateStartNanos) / 1_000_000L
        val cycleLine = phase != CyclePhase.SETTLED
        when (state) {
            State.IDLE -> running = false
            State.TURN -> if (turn == null) enter(State.WAIT, now)
            State.WAIT -> {
                overflow = view.overflowPx()
                if (cycleLine) {
                    val budget = CompactMediaIslandPolicy.cycleBudgetMs(phase)
                    if (overflow <= 0f) {
                        if (elapsed >= CompactMediaIslandPolicy.cycleHoldMs(0f, budget)) advance(view)
                    } else if (elapsed >= CompactMediaIslandPolicy.CYCLE_SCROLL_DELAY_MS) {
                        speed = CompactMediaIslandPolicy.cycleScrollSpeedPxPerSec(
                            overflow,
                            budget,
                            settings.titleScrollSpeed,
                        )
                        enter(State.FORWARD, now)
                    }
                    return
                }
                val limit = loopLimit()
                if (limit == 0 || overflow <= 0f || (limit != null && completedLoops >= limit)) {
                    state = State.IDLE
                    running = false
                    return
                }
                val delay = if (completedLoops == 0) LOOP_START_DELAY_MS else CompactMediaIslandPolicy.TITLE_LOOP_HOLD_MS
                if (elapsed >= delay) {
                    speed = settings.titleScrollSpeed.toFloat()
                    enter(State.FORWARD, now)
                }
            }
            State.FORWARD -> {
                overflow = view.overflowPx()
                val distance = speed * elapsed / 1000f
                if (distance >= overflow) {
                    view.offset = overflow
                    enter(State.HOLD, now)
                } else {
                    view.offset = distance
                }
            }
            State.HOLD -> {
                if (cycleLine) {
                    val budget = CompactMediaIslandPolicy.cycleBudgetMs(phase)
                    if (elapsed >= CompactMediaIslandPolicy.cycleHoldMs(overflow, budget)) advance(view)
                    return
                }
                if (elapsed >= CompactMediaIslandPolicy.TITLE_LOOP_HOLD_MS) enter(State.RETURN, now)
            }
            State.RETURN -> {
                val duration = MarqueeMotion.returnDurationMs(overflow, settings.titleScrollSpeed)
                view.offset = MarqueeMotion.returnOffset(overflow, elapsed, duration)
                if (elapsed >= duration) {
                    view.offset = 0f
                    completedLoops += 1
                    enter(State.WAIT, now)
                }
            }
        }
    }

    private fun loopLimit(): Int? =
        CompactMediaIslandPolicy.scrollLoopLimit(settings.titleScrollMode, settings.titleScrollBounce)

    private fun advance(view: CompactTitleView) {
        phase = CompactMediaIslandPolicy.nextCyclePhase(phase, artistLine.isBlank())
        if (phase == CyclePhase.SETTLED) settledIdentity = identity
        val next = CompactMediaIslandPolicy.cycleText(phase, title, artistLine)
        beginLine(view, next, animate = view.text != next)
    }

    private fun beginLine(view: CompactTitleView, text: String, animate: Boolean) {
        turn?.cancel()
        identity?.let { sharedLine = it to text }
        if (animate) startTurn(view, text) else view.text = text
        onLineChanged(text)
        view.offset = 0f
        completedLoops = 0
        state = if (turn != null) State.TURN else State.WAIT
        stateStartNanos = 0L
    }

    /** Xiaomi's TextSwitcherAnimator: the old line drops out, the new one drops in from above. */
    private fun startTurn(view: CompactTitleView, text: String) {
        val ghost = ghostRef.get()
        if (ghost != null) {
            ghost.paint.color = view.paint.color
            ghost.paint.typeface = view.paint.typeface
            ghost.text = view.text
            ghost.offset = view.offset
            ghost.visibility = View.VISIBLE
        }
        view.text = text
        val distance = view.baselinePx.takeIf { it > 0f } ?: (view.height * 0.7f)
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = CompactMediaIslandPolicy.TURN_DURATION_MS
            interpolator = TimeInterpolator { CompactMediaIslandPolicy.turnProgress(it) }
            addUpdateListener { applyTurn(view, ghost, it.animatedValue as Float, distance) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    resetTurn(view, ghost)
                    if (turn === animation) turn = null
                }
            })
        }
        turn = animator
        applyTurn(view, ghost, 0f, distance)
        animator.start()
    }

    private fun applyTurn(view: View, ghost: View?, progress: Float, distance: Float) {
        val enter = progress.coerceIn(0f, 1.1f)
        transform(
            view = view,
            alpha = enter,
            translationY = -distance * (1f - enter),
            scale = 1f - TURN_SCALE_DROP * (1f - enter),
            rotation = TURN_ROTATION_DEG * (1f - enter),
            blur = TURN_BLUR_PX * (1f - enter),
        )
        if (ghost != null) {
            val exit = progress.coerceIn(0f, 1f)
            transform(
                view = ghost,
                alpha = 1f - exit,
                translationY = distance * exit,
                scale = 1f - TURN_SCALE_DROP * exit,
                rotation = -TURN_ROTATION_DEG * exit,
                blur = TURN_BLUR_PX * exit,
            )
        }
    }

    private fun transform(
        view: View,
        alpha: Float,
        translationY: Float,
        scale: Float,
        rotation: Float,
        blur: Float,
    ) {
        view.pivotX = 0f
        view.pivotY = view.height / 2f
        view.alpha = alpha.coerceIn(0f, 1f)
        view.translationY = translationY
        view.scaleX = scale
        view.scaleY = scale
        view.rotationX = rotation
        val radius = blur.coerceAtLeast(0f)
        view.setRenderEffect(
            if (radius < 0.5f) null else RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL),
        )
    }

    private fun resetTurn(view: View, ghost: CompactTitleView?) {
        transform(view, 1f, 0f, 1f, 0f, 0f)
        if (ghost != null) {
            transform(ghost, 1f, 0f, 1f, 0f, 0f)
            ghost.visibility = View.GONE
            ghost.text = ""
        }
    }

    private fun start() {
        if (running) return
        running = true
        spotKey = Long.MIN_VALUE
        choreographer.removeFrameCallback(this)
        choreographer.postFrameCallback(this)
    }

    /**
     * The line goes back to rest as soon as the island leaves its resting state, so the
     * animation back into the cutout never shows a half-scrolled title.
     */
    private fun pause(view: CompactTitleView) {
        if (paused) return
        paused = true
        restartLine(view)
    }

    private fun restartLine(view: CompactTitleView) {
        if (state == State.FORWARD || state == State.HOLD || state == State.RETURN) {
            view.offset = 0f
            state = State.WAIT
        }
        completedLoops = 0
        stateStartNanos = 0L
    }

    /**
     * True once the island has held still for a moment. Its entry animation from an app
     * (and width changes between tracks) move the slot, and time must not run meanwhile.
     */
    private fun isSettled(view: View, now: Long): Boolean {
        val anchor = view.parent as? View ?: view
        anchor.getLocationInWindow(spot)
        val scale = cumulativeScale(anchor)
        val step = MOVE_STEP_DP * view.resources.displayMetrics.density
        val moving = spotKey == Long.MIN_VALUE ||
            kotlin.math.abs(spot[0] - lastX) > step ||
            kotlin.math.abs(spot[1] - lastY) > step ||
            kotlin.math.abs(scale - lastScale) > MOVE_SCALE_STEP
        spotKey = 0L
        lastX = spot[0]
        lastY = spot[1]
        lastScale = scale
        if (moving) {
            spotSinceNanos = now
            return false
        }
        return now - spotSinceNanos >= SETTLE_NANOS
    }

    private fun cumulativeScale(view: View): Float {
        var scale = 1f
        var current: View? = view
        while (current != null) {
            scale *= current.scaleX
            current = current.parent as? View
        }
        return scale
    }

    /** Picks up after a detach. A half-finished scroll restarts from the beginning of the line. */
    private fun resume() {
        if (running || state == State.IDLE) return
        val view = primaryRef.get() ?: return
        if (state == State.FORWARD || state == State.HOLD || state == State.RETURN) {
            view.offset = 0f
            state = State.WAIT
        }
        stateStartNanos = 0L
        start()
    }

    private fun enter(next: State, now: Long) {
        state = next
        stateStartNanos = now
    }

    /** The primary line's own alpha is part of the turn, so start from its parent. */
    private fun isOnScreen(view: View): Boolean {
        if (!view.isShown || view.windowVisibility != View.VISIBLE) return false
        var alpha = 1f
        var current = view.parent as? View
        while (current != null) {
            alpha *= current.alpha
            if (alpha < MIN_VISIBLE_ALPHA) return false
            current = current.parent as? View
        }
        return true
    }

    private enum class State { IDLE, TURN, WAIT, FORWARD, HOLD, RETURN }

    companion object {
        private const val LOOP_START_DELAY_MS = 800L
        private const val HIDDEN_POLL_MS = 250L
        private const val MIN_VISIBLE_ALPHA = 0.05f
        private const val TURN_SCALE_DROP = 0.2f
        private const val TURN_ROTATION_DEG = 20f
        private const val TURN_BLUR_PX = 30f

        private const val BUSY_POLL_MS = 100L
        private const val SETTLE_NANOS = 200_000_000L
        private const val MOVE_STEP_DP = 1.5f
        private const val MOVE_SCALE_STEP = 0.004f

        /** Track whose cycle already played, shared by the real and transition islands. */
        @Volatile var settledIdentity: String? = null

        /** Line the real island shows for a track, mirrored by the transition copy. */
        @Volatile private var sharedLine: Pair<String, String>? = null
    }
}
