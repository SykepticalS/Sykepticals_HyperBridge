package com.d4viddf.hyperbridge.xposed.mediacard.island.compact

import com.d4viddf.hyperbridge.xposed.mediacard.MediaCardConstants
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure timing and width rules for the native compact media island.
 * SystemUI hooks read this; unit tests cover it without a device.
 */
object CompactMediaIslandPolicy {
    const val TITLE_BUDGET_MS = 3_000L
    const val ARTIST_BUDGET_MS = 5_000L
    const val CYCLE_SCROLL_DELAY_MS = 600L
    const val CYCLE_HOLD_AFTER_SCROLL_MS = 1_000L
    const val FITTING_LINE_HOLD_MS = 1_200L
    const val TITLE_LOOP_HOLD_MS = 1_000L
    const val LIMITED_LEFT_DP = 114f
    const val UNLIMITED_LEFT_DP = 170f
    const val MIN_TEXT_DP = 40f
    const val DEFAULT_END_CLEARANCE_DP = 4f
    const val ARTIST_PREFIX = "By: "

    /** Xiaomi's TextSwitcherAnimator: FolmeEase.spring(0.75, 0.35) settled over 550 ms. */
    const val TURN_DURATION_MS = 550L
    private const val TURN_DAMPING = 0.75
    private const val TURN_RESPONSE_S = 0.35

    enum class CyclePhase {
        TITLE,
        ARTIST,
        SETTLED,
    }

    fun normalizeScrollMode(raw: Int): Int = raw.coerceIn(
        MediaCardConstants.COMPACT_TITLE_SCROLL_OFF,
        MediaCardConstants.COMPACT_TITLE_SCROLL_FOREVER,
    )

    fun normalizeSpeed(raw: Int): Int = raw.coerceIn(
        MediaCardConstants.MIN_HOOK_ISLAND_COMPACT_TITLE_SCROLL_SPEED,
        MediaCardConstants.MAX_HOOK_ISLAND_COMPACT_TITLE_SCROLL_SPEED,
    )

    fun normalizeWidth(raw: Int): Int = raw.coerceIn(
        0,
        MediaCardConstants.MAX_HOOK_ISLAND_COMPACT_WIDTH,
    )

    fun cycleActive(showTitle: Boolean, cycleTitleArtist: Boolean): Boolean =
        showTitle && cycleTitleArtist

    /**
     * Back-and-forth passes of the settled title before it stops.
     * `0` does not scroll. `null` repeats until the track changes, which is also what a
     * stored "off" repeat count means once "Scroll title" is on.
     */
    fun scrollLoopLimit(mode: Int, scrollTitle: Boolean): Int? {
        if (!scrollTitle) return 0
        return when (normalizeScrollMode(mode)) {
            MediaCardConstants.COMPACT_TITLE_SCROLL_ONCE -> 1
            MediaCardConstants.COMPACT_TITLE_SCROLL_TWICE -> 2
            else -> null
        }
    }

    fun shouldResetCycle(previousIdentity: String?, identity: String): Boolean =
        previousIdentity != identity

    fun artistLine(artist: String): String {
        val clean = artist.trim()
        return if (clean.isEmpty()) "" else ARTIST_PREFIX + clean
    }

    /**
     * One forward pass of a cycle line, never slower than the user's speed and never
     * longer than [budgetMs]. Never reverses.
     */
    fun cycleScrollSpeedPxPerSec(overflowPx: Float, budgetMs: Long, minSpeedPxPerSec: Int): Float {
        if (overflowPx <= 0f || budgetMs <= 0L) return 0f
        return maxOf(minSpeedPxPerSec.toFloat(), overflowPx / (budgetMs / 1000f))
    }

    /**
     * Text that fits moves on after a short read, never outlasting the budget.
     * Scrolled text waits a second at the end.
     */
    fun cycleHoldMs(overflowPx: Float, budgetMs: Long): Long =
        if (overflowPx <= 0f) minOf(FITTING_LINE_HOLD_MS, budgetMs) else CYCLE_HOLD_AFTER_SCROLL_MS

    fun nextCyclePhase(current: CyclePhase, artistBlank: Boolean = false): CyclePhase = when (current) {
        CyclePhase.TITLE -> if (artistBlank) CyclePhase.SETTLED else CyclePhase.ARTIST
        CyclePhase.ARTIST -> CyclePhase.SETTLED
        CyclePhase.SETTLED -> CyclePhase.SETTLED
    }

    fun cycleBudgetMs(phase: CyclePhase): Long = when (phase) {
        CyclePhase.TITLE -> TITLE_BUDGET_MS
        CyclePhase.ARTIST -> ARTIST_BUDGET_MS
        CyclePhase.SETTLED -> 0L
    }

    /** [artistLine] is the already prefixed "By: …" line. */
    fun cycleText(phase: CyclePhase, title: String, artistLine: String): String = when (phase) {
        CyclePhase.TITLE, CyclePhase.SETTLED -> title
        CyclePhase.ARTIST -> artistLine.ifBlank { title }
    }

    /** Spring progress for the text turn, `0` at the start and settled at `1`. */
    fun turnProgress(fraction: Float): Float {
        if (fraction <= 0f) return 0f
        if (fraction >= 1f) return 1f
        val t = fraction * TURN_DURATION_MS / 1000.0
        val omega = 2.0 * PI / TURN_RESPONSE_S
        val damped = omega * sqrt(1.0 - TURN_DAMPING * TURN_DAMPING)
        val decay = exp(-TURN_DAMPING * omega * t)
        val offset = decay * (cos(damped * t) + TURN_DAMPING * omega / damped * sin(damped * t))
        return (1.0 - offset).toFloat()
    }

    /**
     * `null` leaves Xiaomi's max width alone. Each step adds a slice of the room between
     * the system cap (114dp) and the unconstrained cap (170dp).
     */
    fun islandMaxOverridePx(percent: Int, systemMaxPx: Float, density: Float): Float? {
        val width = normalizeWidth(percent)
        if (width <= 0) return null
        return systemMaxPx + extraDp(width) * density.coerceAtLeast(0.5f)
    }

    /** Whole left slot (album cover plus text), in pixels. */
    fun leftSlotMaxPx(percent: Int, density: Float): Int {
        val dp = LIMITED_LEFT_DP + extraDp(normalizeWidth(percent))
        return (dp * density.coerceAtLeast(0.5f)).toInt().coerceAtLeast(1)
    }

    /** Room for the title once the album cover and its gap ([leadingPx]) are taken out. */
    fun textViewportPx(percent: Int, density: Float, leadingPx: Int): Int {
        val minimum = (MIN_TEXT_DP * density.coerceAtLeast(0.5f)).toInt()
        return (leftSlotMaxPx(percent, density) - leadingPx.coerceAtLeast(0)).coerceAtLeast(minimum)
    }

    /**
     * Empty room after the album cover when the title is off. It grows evenly with the slider,
     * reaching the room a full-length title gets at the maximum setting.
     */
    fun spacerWidthPx(percent: Int, density: Float, leadingPx: Int): Int {
        val width = normalizeWidth(percent)
        val max = MediaCardConstants.MAX_HOOK_ISLAND_COMPACT_WIDTH
        val full = textViewportPx(max, density, leadingPx)
        return (full * width / max).coerceAtLeast(1)
    }

    /**
     * One width for the whole track: the widest line the island will show, capped at the
     * viewport, so the island does not jump between the title and the artist.
     */
    fun slotWidthPx(lineWidthsPx: List<Float>, horizontalPaddingPx: Int, viewportPx: Int): Int {
        val widest = lineWidthsPx.maxOrNull() ?: 0f
        val wanted = ceil(widest).toInt() + horizontalPaddingPx.coerceAtLeast(0)
        return wanted.coerceIn(1, viewportPx.coerceAtLeast(1))
    }

    /**
     * Default length follows the line currently on screen. A raised length slider reserves one
     * stable slot for every line in the cycle so explicit user sizing never jumps.
     */
    fun displayedSlotWidthPx(
        percent: Int,
        displayedLineWidthPx: Float,
        lineWidthsPx: List<Float>,
        horizontalPaddingPx: Int,
        viewportPx: Int,
    ): Int = slotWidthPx(
        lineWidthsPx = if (normalizeWidth(percent) == 0) {
            listOf(displayedLineWidthPx)
        } else {
            lineWidthsPx
        },
        horizontalPaddingPx = horizontalPaddingPx,
        viewportPx = viewportPx,
    )

    /** Keeps the final glyph just outside the camera-side fade in dynamic Default mode. */
    fun defaultEndClearancePx(percent: Int, density: Float): Int =
        if (normalizeWidth(percent) == 0) {
            (DEFAULT_END_CLEARANCE_DP * density.coerceAtLeast(0.5f)).toInt()
        } else {
            0
        }

    private fun extraDp(width: Int): Float = (UNLIMITED_LEFT_DP - LIMITED_LEFT_DP) * (width / 100f)
}

data class CompactMediaIslandSettings(
    val showTitle: Boolean,
    val titleScrollMode: Int,
    val titleScrollSpeed: Int,
    val titleScrollBounce: Boolean,
    val cycleTitleArtist: Boolean,
    val widthPercent: Int,
) {
    val cycleActive: Boolean
        get() = CompactMediaIslandPolicy.cycleActive(showTitle, cycleTitleArtist)

    companion object {
        fun defaults(): CompactMediaIslandSettings = fromRaw(
            showTitle = MediaCardConstants.DEFAULT_HOOK_ISLAND_COMPACT_SHOW_TITLE,
            scrollMode = MediaCardConstants.DEFAULT_HOOK_ISLAND_COMPACT_TITLE_SCROLL_MODE,
            speed = MediaCardConstants.DEFAULT_HOOK_ISLAND_COMPACT_TITLE_SCROLL_SPEED,
            bounce = MediaCardConstants.DEFAULT_HOOK_ISLAND_COMPACT_TITLE_SCROLL_BOUNCE,
            cycle = MediaCardConstants.DEFAULT_HOOK_ISLAND_COMPACT_CYCLE_TITLE_ARTIST,
            width = MediaCardConstants.DEFAULT_HOOK_ISLAND_COMPACT_WIDTH,
        )

        fun fromRaw(
            showTitle: Boolean,
            scrollMode: Int,
            speed: Int,
            bounce: Boolean,
            cycle: Boolean,
            width: Int,
        ): CompactMediaIslandSettings = CompactMediaIslandSettings(
            showTitle = showTitle,
            titleScrollMode = CompactMediaIslandPolicy.normalizeScrollMode(scrollMode),
            titleScrollSpeed = CompactMediaIslandPolicy.normalizeSpeed(speed),
            titleScrollBounce = bounce,
            cycleTitleArtist = cycle,
            widthPercent = CompactMediaIslandPolicy.normalizeWidth(width),
        )
    }
}
