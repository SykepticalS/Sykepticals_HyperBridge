package com.lidesheng.hyperlyric.root.island.content

import android.view.View
import java.util.WeakHashMap

/**
 * Keeps one content signature per injected slot so switching between lyric and metadata modes
 * cannot leave two independent caches claiming that the same View is already up to date.
 */
internal object IslandSlotContentSignatureCache {
    private val signatures = WeakHashMap<View, String>()

    fun get(view: View): String? = signatures[view]

    fun set(view: View, signature: String) {
        signatures[view] = signature
    }

    fun invalidate(view: View? = null) {
        if (view == null) {
            synchronized(signatures) { signatures.clear() }
        } else {
            synchronized(signatures) { signatures.remove(view) }
        }
    }
}
