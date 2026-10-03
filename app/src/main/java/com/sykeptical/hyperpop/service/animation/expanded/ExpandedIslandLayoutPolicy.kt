package com.sykeptical.hyperpop.service.animation.expanded

/**
 * Portrait takeover geometry for an expanded island.
 *
 * The card keeps Xiaomi's expanded width and the compact island's top, then
 * grows downward. With no measured content, placement stays on the
 * conservative cutout gap. A measured profile may start closer, still clear
 * of the camera, and a rounded pill may trim empty space below the last leaf.
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
        val legacySafety = (CUTOUT_SAFETY_DP * request.density).toInt()
        val cardTop = request.compact.top
        val cardLeft = request.nativeExpanded.left
        val cardRight = request.nativeExpanded.right
        if (cardRight <= cardLeft) return ExpandedLayoutDecision.Native
        if (!request.cutout.intersects(IslandRect(cardLeft, cardTop, cardRight, request.displayHeight))) {
            return ExpandedLayoutDecision.Native
        }

        val profile = request.content?.takeIf {
            it.contentWidthPx > 0 && it.contentHeightPx > 0 && it.leaves.isNotEmpty()
        }
        val radiusForPlacement = if (request.style.roundedPill) {
            ExpandedPillPolicy.radiusCap(request.nativeExpanded.height, request.nativeRadiusPx, request.density)
        } else {
            request.nativeRadiusPx
        }
        val placement = profile?.let {
            CutoutSafeLayout.solve(
                cardLeft, cardRight, cardTop, request.cutout, request.density, radiusForPlacement, it,
            )
        }
        val legacyTop = maxOf(request.compact.bottom + gap, request.cutout.bottom + legacySafety)
        if (placement == null && legacyTop <= cardTop) return ExpandedLayoutDecision.Native
        val contentTop = (placement?.contentOriginY ?: legacyTop).coerceAtLeast(cardTop)
        val provisionalBottom = if (profile == null) {
            contentTop + request.nativeExpanded.height
        } else {
            val delta = contentTop - cardTop - profile.nativeTopMarginPx
            cardTop + request.nativeExpanded.height + delta
        }
        if (provisionalBottom <= contentTop || provisionalBottom > request.displayHeight) {
            return ExpandedLayoutDecision.Native
        }
        val contentLeft = CutoutSafeLayout.contentLeft(
            cardLeft,
            cardRight,
            profile?.contentWidthPx ?: (cardRight - cardLeft),
        )
        val pill = ExpandedPillPolicy.apply(
            enabled = request.style.roundedPill,
            cardLeft = cardLeft,
            cardRight = cardRight,
            cardTop = cardTop,
            provisionalBottom = provisionalBottom,
            contentOriginY = contentTop,
            contentLeft = contentLeft,
            nativeRadiusPx = request.nativeRadiusPx,
            density = request.density,
            profile = profile,
            displayHeight = request.displayHeight,
        )
        if (pill.cardBottom <= contentTop || pill.cardBottom > request.displayHeight) {
            return ExpandedLayoutDecision.Native
        }
        val card = IslandRect(cardLeft, cardTop, cardRight, pill.cardBottom)
        val bodyOffset = if (profile == null) contentTop - cardTop else contentTop - cardTop - profile.nativeTopMarginPx
        val pad = if (profile == null) {
            legacySafety
        } else {
            (CutoutSafeLayout.HORIZONTAL_PAD_DP * request.density).toInt()
        }
        val leadingSafe = IslandRect(
            left = card.left + pad,
            top = card.top,
            right = request.cutout.left - pad,
            bottom = contentTop,
        ).takeIf { !it.isEmpty() } ?: IslandRect(0, 0, 0, 0)
        val trailingSafe = IslandRect(
            left = request.cutout.right + pad,
            top = card.top,
            right = card.right - pad,
            bottom = contentTop,
        ).takeIf { !it.isEmpty() } ?: IslandRect(0, 0, 0, 0)
        val exclusion = request.cutout.inflate(pad)
        val leading = placeEar(request.leadingEar, leadingSafe, exclusion)
        val trailing = placeEar(request.trailingEar, trailingSafe, exclusion)
        val lifts = placement?.sideLifts.orEmpty()
        val below = IslandRect(card.left, contentTop, card.right, card.bottom)
        val touch = buildList {
            add(card)
            if (!below.isEmpty() && below != card) add(below)
            leading?.let(::add)
            trailing?.let(::add)
            if (profile != null) addAll(liftRects(profile, contentLeft, contentTop, lifts))
        }
        return ExpandedLayoutDecision.Takeover(
            card = card,
            bodyOffsetPx = bodyOffset,
            bodyTop = contentTop,
            leadingEar = leading,
            trailingEar = trailing,
            leadingSafe = leadingSafe,
            trailingSafe = trailingSafe,
            belowCutout = below,
            touchRegions = touch,
            contentOffsetPx = if (profile == null) bodyOffset else contentTop - cardTop,
            nativeRadiusPx = request.nativeRadiusPx,
            radiusPx = pill.radiusPx,
            contentScale = pill.contentScale,
            flowMask = if (request.style.blackBackground) {
                ExpandedSurfaceStyle.flowMask(request.cutout, card.bottom, request.density)
            } else {
                null
            },
            sideLifts = lifts,
            pillEnabled = request.style.roundedPill,
            blackBackground = request.style.blackBackground,
            tightLayout = profile != null,
        )
    }

    fun collapseTarget(compact: IslandRect): IslandRect = compact

    /**
     * App-exit can leave the big-island rect below the camera for a few
     * seconds. Seat that rect on the cutout and let [decide] run again.
     * A compact rect that already meets the hole is unchanged.
     */
    fun seatOnCutout(request: ExpandedLayoutRequest): ExpandedLayoutRequest {
        val compact = request.compact
        val cutout = request.cutout
        if (compact.isEmpty() || cutout.isEmpty() || compact.top < cutout.bottom) return request
        val left = cutout.centerX - compact.width / 2
        val top = cutout.centerY - compact.height / 2
        return request.copy(compact = IslandRect(left, top, left + compact.width, top + compact.height))
    }

    fun decideSeated(request: ExpandedLayoutRequest): ExpandedLayoutDecision {
        val seated = seatOnCutout(request)
        val decision = decide(seated)
        return if (seated === request || decision is ExpandedLayoutDecision.Takeover) decision else decide(request)
    }

    private fun liftRects(
        profile: ExpandedContentProfile,
        contentLeft: Int,
        originY: Int,
        lifts: List<SideLift>,
    ): List<IslandRect> {
        return lifts.mapNotNull { lift ->
            val cluster = profile.clusters.firstOrNull { it.index == lift.clusterIndex } ?: return@mapNotNull null
            cluster.bounds.translate(contentLeft, originY + lift.translationY)
        }
    }

    private fun placeEar(ear: IslandRect?, safe: IslandRect, exclusion: IslandRect): IslandRect? {
        if (ear == null || ear.isEmpty() || safe.isEmpty()) return null
        if (ear.width > safe.width || ear.height > safe.height) return null
        val shiftX = safe.left - ear.left
        val placed = ear.translate(shiftX, safe.top - ear.top)
        if (!safe.contains(placed) || placed.intersects(exclusion)) return null
        return placed
    }
}
