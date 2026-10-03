package com.sykeptical.hyperpop.service.animation.expanded

/**
 * Portrait takeover geometry for an expanded island.
 *
 * The card keeps Xiaomi's expanded width and the compact island's top, then
 * grows downward. Content starts below the camera hole and the compact ears.
 * Collapse is the compact rect unchanged, so Xiaomi's own collapse animation
 * lands on native geometry.
 */
object ExpandedIslandLayoutPolicy {
    private const val BODY_GAP_DP = 4f
    private const val CUTOUT_SAFETY_DP = 8f

    fun decide(request: ExpandedLayoutRequest): ExpandedLayoutDecision {
        if (!request.enabled || !request.portrait || request.keyguard || request.tablet) {
            return ExpandedLayoutDecision.Native
        }
        if (request.density <= 0f || request.displayWidth <= 0 || request.displayHeight <= 0) {
            return ExpandedLayoutDecision.Native
        }
        if (request.cutout.isEmpty() || request.compact.isEmpty() || request.nativeExpanded.isEmpty()) {
            return ExpandedLayoutDecision.Native
        }
        if (request.statusBarHeight <= 0) return ExpandedLayoutDecision.Native

        val gap = (BODY_GAP_DP * request.density).toInt()
        val safety = (CUTOUT_SAFETY_DP * request.density).toInt()
        val cardTop = request.compact.top
        val cardLeft = request.nativeExpanded.left
        val cardRight = request.nativeExpanded.right
        if (cardRight <= cardLeft) return ExpandedLayoutDecision.Native
        val card = IslandRect(
            left = cardLeft,
            top = cardTop,
            right = cardRight,
            bottom = 0,
        )
        if (!request.cutout.intersects(card.copy(bottom = request.displayHeight))) {
            return ExpandedLayoutDecision.Native
        }

        val bodyTop = maxOf(
            request.compact.bottom + gap,
            request.cutout.bottom + safety,
        )
        if (bodyTop <= cardTop) return ExpandedLayoutDecision.Native
        val cardBottom = bodyTop + request.nativeExpanded.height
        if (cardBottom <= bodyTop || cardBottom > request.displayHeight) {
            return ExpandedLayoutDecision.Native
        }
        val resolved = card.copy(bottom = cardBottom)
        val bodyOffset = bodyTop - cardTop
        val pad = safety
        val leadingSafe = IslandRect(
            left = resolved.left + pad,
            top = resolved.top,
            right = request.cutout.left - safety,
            bottom = bodyTop,
        ).takeIf { !it.isEmpty() } ?: IslandRect(0, 0, 0, 0)
        val trailingSafe = IslandRect(
            left = request.cutout.right + safety,
            top = resolved.top,
            right = resolved.right - pad,
            bottom = bodyTop,
        ).takeIf { !it.isEmpty() } ?: IslandRect(0, 0, 0, 0)
        val exclusion = request.cutout.inflate(safety)
        val leading = placeEar(request.leadingEar, leadingSafe, exclusion)
        val trailing = placeEar(request.trailingEar, trailingSafe, exclusion)
        val below = IslandRect(resolved.left, bodyTop, resolved.right, resolved.bottom)
        val touch = buildList {
            add(below)
            leading?.let(::add)
            trailing?.let(::add)
        }
        return ExpandedLayoutDecision.Takeover(
            card = resolved,
            bodyOffsetPx = bodyOffset,
            bodyTop = bodyTop,
            leadingEar = leading,
            trailingEar = trailing,
            leadingSafe = leadingSafe,
            trailingSafe = trailingSafe,
            belowCutout = below,
            touchRegions = touch,
        )
    }

    fun collapseTarget(compact: IslandRect): IslandRect = compact

    private fun placeEar(ear: IslandRect?, safe: IslandRect, exclusion: IslandRect): IslandRect? {
        if (ear == null || ear.isEmpty() || safe.isEmpty()) return null
        if (ear.width > safe.width || ear.height > safe.height) return null
        val shiftX = safe.left - ear.left
        val placed = ear.translate(shiftX, safe.top - ear.top)
        if (!safe.contains(placed) || placed.intersects(exclusion)) return null
        return placed
    }
}
