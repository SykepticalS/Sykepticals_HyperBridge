package com.sykeptical.hyperpop.xposed.hooks

import com.sykeptical.hyperpop.service.animation.expanded.IslandRect

/** Latest portrait takeover geometry, for layout appliers that run after a rebind. */
object ExpandedVisualSession {
    @Volatile var active: Boolean = false
    @Volatile var pill: Boolean = false
    @Volatile var rtl: Boolean = false
    @Volatile var cutout: IslandRect? = null

    fun publish(cutout: IslandRect, pill: Boolean, rtl: Boolean) {
        this.cutout = cutout
        this.pill = pill
        this.rtl = rtl
        active = true
    }

    fun clear() {
        active = false
        pill = false
        rtl = false
        cutout = null
    }
}
