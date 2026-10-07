package com.sykeptical.hyperpop.xposed.hooks.fingerprint

import android.content.Context
import android.graphics.Canvas
import android.graphics.RenderNode
import android.os.SystemClock
import android.view.View
import com.sykeptical.hyperpop.service.animation.fingerprint.FingerprintIslandGeometry
import com.sykeptical.hyperpop.service.animation.fingerprint.FingerprintPhase
import com.sykeptical.hyperpop.service.animation.fingerprint.IslandShake
import com.sykeptical.hyperpop.service.animation.fingerprint.IslandSpring
import com.sykeptical.hyperpop.service.animation.fingerprint.LottieFingerprintParser
import kotlin.math.abs

/**
 * HyperPop-owned child of Xiaomi's island window. It springs between the camera
 * hole, the lock pill, and the square, and it never accepts touches.
 */
internal class FingerprintIslandView(context: Context) : View(context) {
    interface Listener {
        fun onSuccessSettled(generation: Long)
        fun onCollapseFinished(generation: Long)
        fun onOrientationChanged()
    }

    var listener: Listener? = null

    private val renderer = FingerprintIslandRenderer()
    private val blurNode = RenderNode("hyperpop-fp")
    private val springOut = FloatArray(2)
    private val left = Channel()
    private val top = Channel()
    private val right = Channel()
    private val bottom = Channel()
    private val radius = Channel()
    private val blue = Channel(1f)
    private val success = Channel(0f)
    private val shakeBack = Channel(0f)

    private var phase = FingerprintPhase.Hidden
    private var generation = 0L
    private var fingerDown = false
    private var fillFrame = 0f
    private var showBlue = false
    private var shaking = false
    private var shakeSerial = 0
    private var playingSuccess = false
    private var animationsEnabled = true
    private var layout: FingerprintIslandGeometry.Layout? = null
    private var artwork: LottieFingerprintParser.Artwork? = null
    private var shakeStartedMs = 0L
    private var shakeActiveSerial = -1
    private var shakingBack = false
    private var glyphExtra = 0f
    private var lastFrameMs = 0L
    private var positioned = false
    private var successNotified = false
    private var collapseNotified = false
    private var lastOrientation = resources.configuration.orientation

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
        visibility = GONE
    }

    fun bind(
        phase: FingerprintPhase,
        generation: Long,
        fingerDown: Boolean,
        showBlue: Boolean,
        shaking: Boolean,
        shakeSerial: Int,
        playingSuccess: Boolean,
        layout: FingerprintIslandGeometry.Layout,
        artwork: LottieFingerprintParser.Artwork?,
        animationsEnabled: Boolean,
    ) {
        val generationChanged = generation != this.generation
        val showBlueRising = showBlue && !this.showBlue
        this.phase = phase
        this.generation = generation
        this.fingerDown = fingerDown
        this.showBlue = showBlue
        if (showBlueRising) blue.snap(1f)
        this.shaking = shaking
        this.shakeSerial = shakeSerial
        this.playingSuccess = playingSuccess
        this.layout = layout
        this.artwork = artwork
        this.animationsEnabled = animationsEnabled
        if (generationChanged) {
            fillFrame = 0f
            successNotified = false
            collapseNotified = false
        }
        if (!positioned) {
            snap(layout.hole, layout.hole.height.coerceAtLeast(1f) / 2f)
            positioned = true
        }
        retarget(layout)
        if (shaking && shakeSerial != shakeActiveSerial) {
            shakeActiveSerial = shakeSerial
            shakeStartedMs = SystemClock.uptimeMillis()
            shakingBack = false
        } else if (!shaking && !shakingBack && (translationX != 0f || glyphExtra != 0f)) {
            shakingBack = true
            shakeBack.snap(translationX)
            shakeBack.target = 0f
        }
        visibility = if (phase == FingerprintPhase.Hidden) GONE else VISIBLE
        postInvalidateOnAnimation()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation != lastOrientation) {
            lastOrientation = newConfig.orientation
            listener?.onOrientationChanged()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val current = layout ?: return
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrameMs == 0L) 0.016f else ((now - lastFrameMs) / 1000f).coerceIn(0f, 0.032f)
        lastFrameMs = now
        stepPlate(dt, animationsEnabled)
        if (fingerDown && phase == FingerprintPhase.Scanning) {
            fillFrame = LottieFingerprintParser.advanceFill(fillFrame, dt)
        }
        stepChannel(blue, if (showBlue) 1f else 0f, dt, IslandSpring.CHANGE_DAMPING, IslandSpring.CHANGE_RESPONSE)
        val wantSuccess = when (phase) {
            FingerprintPhase.Success, FingerprintPhase.SuccessHold -> 1f
            FingerprintPhase.Collapsing -> if (success.value > 0.05f || playingSuccess) 1f else 0f
            else -> 0f
        }
        stepChannel(success, wantSuccess, dt, IslandSpring.APPEAR_DAMPING, IslandSpring.APPEAR_RESPONSE)
        stepShake(now, dt, current)
        val plate = FingerprintIslandGeometry.Rect(left.value, top.value, right.value, bottom.value)
        val morph = morph(plate, current)
        val glyph = FingerprintIslandGeometry.Rect(
            current.glyph.left + plate.centerX - current.square.centerX,
            current.glyph.top + plate.top - current.square.top,
            current.glyph.right + plate.centerX - current.square.centerX,
            current.glyph.bottom + plate.top - current.square.top,
        )
        val padlock = FingerprintIslandGeometry.Rect(
            current.padlock.left + plate.centerX - current.pill.centerX,
            current.padlock.top + plate.centerY - current.pill.centerY,
            current.padlock.right + plate.centerX - current.pill.centerX,
            current.padlock.bottom + plate.centerY - current.pill.centerY,
        )
        val collapsing = phase == FingerprintPhase.Collapsing || phase == FingerprintPhase.Hidden
        val plateAlpha = if (collapsing) {
            val holeSpan = (current.pill.width - current.hole.width).coerceAtLeast(1f)
            ((plate.width - current.hole.width) / holeSpan).coerceIn(0f, 1f)
        } else {
            1f
        }
        renderer.draw(
            canvas,
            FingerprintIslandRenderer.Frame(
                plate = plate,
                glyph = glyph,
                padlock = padlock,
                radius = radius.value,
                strokePx = current.strokePx,
                strokeColor = current.strokeColor,
                plateAlpha = plateAlpha,
                glyphAlpha = morph,
                lockAlpha = 1f - morph,
                blueAlpha = blue.value,
                checkAlpha = success.value,
                success = success.value,
                glyphExtraPx = glyphExtra,
                maxBlurPx = 12f * resources.displayMetrics.density,
                density = resources.displayMetrics.density,
                viewWidth = width.coerceAtLeast(1),
                viewHeight = height.coerceAtLeast(1),
                artwork = artwork,
                fillFrame = fillFrame,
                blurNode = blurNode,
            ),
        )
        notifyMilestones(current)
        if (!idle()) postInvalidateOnAnimation() else if (phase == FingerprintPhase.Hidden) visibility = GONE
    }

    private fun notifyMilestones(current: FingerprintIslandGeometry.Layout) {
        if (phase == FingerprintPhase.Success &&
            !successNotified &&
            IslandSpring.settled(success.value, success.velocity, 1f, 0.02f)
        ) {
            successNotified = true
            listener?.onSuccessSettled(generation)
        }
        if (phase == FingerprintPhase.Collapsing && !collapseNotified && settledPlate()) {
            val nearHole = abs(left.value - current.hole.left) < 1.5f && abs(right.value - current.hole.right) < 1.5f
            if (nearHole) {
                collapseNotified = true
                listener?.onCollapseFinished(generation)
            }
        }
    }

    private fun stepPlate(dt: Float, animated: Boolean) {
        val damping = if (phase == FingerprintPhase.Collapsing || phase == FingerprintPhase.Hidden) {
            IslandSpring.HIDDEN_DAMPING
        } else if (phase == FingerprintPhase.Scanning || phase == FingerprintPhase.LockPill) {
            IslandSpring.CHANGE_DAMPING
        } else {
            IslandSpring.SHOW_DAMPING
        }
        val response = if (phase == FingerprintPhase.Collapsing || phase == FingerprintPhase.Hidden) {
            IslandSpring.HIDDEN_RESPONSE
        } else if (phase == FingerprintPhase.Scanning || phase == FingerprintPhase.LockPill) {
            IslandSpring.CHANGE_RESPONSE
        } else {
            IslandSpring.SHOW_RESPONSE
        }
        if (!animated) {
            left.snap(left.target)
            top.snap(top.target)
            right.snap(right.target)
            bottom.snap(bottom.target)
            radius.snap(radius.target)
            return
        }
        left.step(damping, response, dt, springOut)
        top.step(damping, response, dt, springOut)
        right.step(damping, response, dt, springOut)
        bottom.step(damping, response, dt, springOut)
        radius.step(damping, response, dt, springOut)
    }

    private fun stepChannel(channel: Channel, target: Float, dt: Float, damping: Float, response: Float) {
        channel.target = target
        if (!animationsEnabled) {
            channel.snap(target)
            return
        }
        channel.step(damping, response, dt, springOut)
    }

    private fun stepShake(now: Long, dt: Float, current: FingerprintIslandGeometry.Layout) {
        if (shaking && animationsEnabled) {
            val offsets = IslandShake.offsets(now - shakeStartedMs, current.shakeAmplitudePx, true)
            translationX = offsets.platePx
            glyphExtra = offsets.glyphExtraPx
            return
        }
        if (!animationsEnabled) {
            translationX = 0f
            glyphExtra = 0f
            shakingBack = false
            return
        }
        if (shakingBack) {
            shakeBack.target = 0f
            shakeBack.step(IslandSpring.CHANGE_DAMPING, IslandSpring.CHANGE_RESPONSE, dt, springOut)
            translationX = shakeBack.value
            glyphExtra = shakeBack.value * (IslandShake.GLYPH_FACTOR - 1f)
            if (IslandSpring.settled(shakeBack.value, shakeBack.velocity, 0f, 0.2f)) {
                translationX = 0f
                glyphExtra = 0f
                shakingBack = false
            }
        }
    }

    private fun retarget(current: FingerprintIslandGeometry.Layout) {
        val target = when (phase) {
            FingerprintPhase.Hidden, FingerprintPhase.Collapsing -> current.hole
            FingerprintPhase.LockPill -> current.pill
            else -> current.square
        }
        val targetRadius = when (phase) {
            FingerprintPhase.Hidden, FingerprintPhase.Collapsing -> current.hole.height.coerceAtLeast(1f) / 2f
            FingerprintPhase.LockPill -> current.pillRadius
            else -> current.squareRadius
        }
        left.target = target.left
        top.target = target.top
        right.target = target.right
        bottom.target = target.bottom
        radius.target = targetRadius
    }

    private fun snap(rect: FingerprintIslandGeometry.Rect, corner: Float) {
        left.snap(rect.left)
        top.snap(rect.top)
        right.snap(rect.right)
        bottom.snap(rect.bottom)
        radius.snap(corner)
    }

    private fun morph(plate: FingerprintIslandGeometry.Rect, current: FingerprintIslandGeometry.Layout): Float {
        val span = (current.square.width - current.pill.width).coerceAtLeast(1f)
        return ((plate.width - current.pill.width) / span).coerceIn(0f, 1f)
    }

    private fun settledPlate(): Boolean =
        IslandSpring.settled(left.value, left.velocity, left.target, 0.6f) &&
            IslandSpring.settled(top.value, top.velocity, top.target, 0.6f) &&
            IslandSpring.settled(right.value, right.velocity, right.target, 0.6f) &&
            IslandSpring.settled(bottom.value, bottom.velocity, bottom.target, 0.6f)

    private fun filling(): Boolean =
        fingerDown &&
            phase == FingerprintPhase.Scanning &&
            fillFrame < LottieFingerprintParser.FILL_END_FRAME - 0.05f

    private fun idle(): Boolean = settledPlate() &&
        IslandSpring.settled(blue.value, blue.velocity, blue.target, 0.02f) &&
        IslandSpring.settled(success.value, success.velocity, success.target, 0.02f) &&
        !shaking &&
        !shakingBack &&
        !filling()

    private class Channel(var value: Float = 0f) {
        var velocity: Float = 0f
        var target: Float = value

        fun snap(next: Float) {
            value = next
            target = next
            velocity = 0f
        }

        fun step(damping: Float, response: Float, dt: Float, out: FloatArray) {
            IslandSpring.stepInto(damping, response, value, velocity, target, dt, out)
            value = out[0]
            velocity = out[1]
        }
    }
}
