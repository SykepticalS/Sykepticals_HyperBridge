package com.sykeptical.hyperpop.xposed.hooks.fingerprint

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.sykeptical.hyperpop.service.animation.fingerprint.LottieFingerprintParser
import com.sykeptical.hyperpop.xposed.log
import io.github.libxposed.api.XposedModule
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reads `com.android.settings` `finger_enroll_dark` on a background thread.
 * A missing resource or a schema miss disables the feature for this process.
 */
internal object FingerprintArtworkLoader {
    @Volatile var artwork: LottieFingerprintParser.Artwork? = null
        private set
    @Volatile var failed: Boolean = false
        private set

    private val started = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())

    fun ensure(context: Context, module: XposedModule?, onReady: () -> Unit) {
        if (artwork != null || failed) {
            onReady()
            return
        }
        if (!started.compareAndSet(false, true)) return
        Thread({
            val parsed = runCatching { load(context) }.onFailure {
                module?.log("HyperPop: fingerprint artwork disabled: ${it.message}")
            }.getOrNull()
            if (parsed == null) failed = true else artwork = parsed
            main.post(onReady)
        }, "hyperpop-fp-artwork").start()
    }

    private fun load(context: Context): LottieFingerprintParser.Artwork {
        val settings = context.createPackageContext(
            "com.android.settings",
            Context.CONTEXT_IGNORE_SECURITY,
        )
        val id = settings.resources.getIdentifier(
            "finger_enroll_dark",
            "raw",
            "com.android.settings",
        )
        if (id == 0) error("finger_enroll_dark missing")
        val text = settings.resources.openRawResource(id).bufferedReader().use { it.readText() }
        return LottieFingerprintParser.parse(text) ?: error("finger_enroll_dark schema")
    }
}
