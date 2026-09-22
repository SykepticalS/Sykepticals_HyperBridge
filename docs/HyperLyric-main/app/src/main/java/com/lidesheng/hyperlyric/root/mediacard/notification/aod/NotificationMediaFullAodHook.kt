package com.lidesheng.hyperlyric.root.mediacard.notification.aod

import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker

/**
 * Keeps a notification media card in its expanded presentation while Full AOD is active. The
 * target method otherwise hides the progress row and switches the card to the dedicated 138dp
 * Full AOD height.
 */
internal class NotificationMediaFullAodHook(
    private val keepExpanded: () -> Boolean,
    private val onApplied: (controller: Any) -> Unit,
    private val onFailure: (Throwable) -> Unit
) : Hooker {
    override fun intercept(chain: Chain): Any? {
        val controller = chain.thisObject ?: return chain.proceed()
        val enteringFullAod = chain.args.firstOrNull() as? Boolean == true
        val result = if (enteringFullAod && keepExpanded()) {
            chain.proceed(arrayOf<Any?>(false))
        } else {
            chain.proceed()
        }
        runCatching { onApplied(controller) }.onFailure(onFailure)
        return result
    }
}
