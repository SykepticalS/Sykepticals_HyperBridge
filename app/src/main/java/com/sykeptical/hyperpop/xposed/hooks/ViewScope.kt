package com.sykeptical.hyperpop.xposed.hooks

import android.view.View

private const val MAX_SCOPE_DEPTH = 32

/** True when this view is [scope] or sits under it. */
internal fun View.isWithin(scope: View): Boolean {
    var current: View? = this
    var depth = 0
    while (current != null && depth < MAX_SCOPE_DEPTH) {
        if (current === scope) return true
        current = current.parent as? View
        depth += 1
    }
    return false
}

internal fun <T> Map<View, T>.keysWithin(scopes: List<View>): Set<View> =
    synchronized(this) { keys.filter { key -> scopes.any { key.isWithin(it) } }.toSet() }
