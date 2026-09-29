package com.d4viddf.hyperbridge.xposed.hooks

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Rect
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import android.view.View
import com.d4viddf.hyperbridge.island.backend.IslandProtocol
import com.d4viddf.hyperbridge.service.permanent.PermanentIslandSession
import com.d4viddf.hyperbridge.xposed.HookConfig
import com.d4viddf.hyperbridge.xposed.log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

/**
 * Forces Xiaomi's native show-once/cutout close target and updates the permanent anchor's own
 * DynamicIslandData at the launcher's exact close endpoint. The anchor key/view never changes.
 */
object PermanentIslandHook {
    private const val WINDOW_CONTROLLER =
        "miui.systemui.dynamicisland.window.DynamicIslandWindowViewController"
    private const val CONTENT_VIEW =
        "miui.systemui.dynamicisland.window.content.DynamicIslandContentView"
    private const val WINDOW_VIEW =
        "miui.systemui.dynamicisland.window.DynamicIslandWindowView"
    private const val FOCUS_CONTROLLER =
        "com.android.systemui.statusbar.notification.focus.FocusNotificationController"
    private const val EVENT_COORDINATOR =
        "miui.systemui.dynamicisland.event.DynamicIslandEventCoordinator"

    private const val REQUEST_CLOSE_POSITION = "request_close_position"
    private const val CLOSE_APP_START = "close_app_start"
    private const val CLOSE_APP_END = "close_app_end"
    private const val APP_TO_RECENT = "app_to_recent"
    private const val POSITION = "position"
    private const val PACKAGE_NAME = "packageName"
    private val SECONDARY_HIDDEN_TRANSITIONS = setOf(
        "big_to_hidden",
        "small_to_hidden",
        "expanded_to_hidden",
        "app_to_hidden",
        "sub_app_to_hidden",
        "mini_window_to_hidden",
        "sub_mini_window_to_hidden",
    )

    private val pluginLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>()),
    )
    private val focusLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>()),
    )
    private data class PluginSource(
        val contentView: Any,
        val data: Any,
        val key: String,
    )

    private val session = PermanentIslandSession()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val secondarySources = ConcurrentHashMap<String, String>()
    @Volatile private var context = WeakReference<Context>(null)
    @Volatile private var controllerRef = WeakReference<Any>(null)
    // Keep one bounded strong snapshot for the plugin lifetime. A WeakReference made the only
    // blank state collectible, which could strand stale adopted content with nothing to restore.
    @Volatile private var blankAnchorData: Any? = null
    @Volatile private var pendingSourceData: Any? = null
    @Volatile private var pendingGeneration = -1L
    @Volatile private var appliedGeneration = -1L
    @Volatile private var anchorSuspendedForSecondary = false
    @Volatile private var mediaController: MediaController? = null
    @Volatile private var mediaCallback: MediaController.Callback? = null
    @Volatile private var mediaSourceKey: String? = null
    private val updatingAnchor = ThreadLocal.withInitial { false }

    fun install(module: XposedModule, param: PackageLoadedParam) {
        hookFocusRemoval(module, param.defaultClassLoader)
        hookPlugin(module, param.defaultClassLoader)
        DynamicClassLoaderHooks.observe(module, param.defaultClassLoader) { loader ->
            hookFocusRemoval(module, loader)
            hookPlugin(module, loader)
        }
    }

    fun onNotificationRemoved(module: XposedModule, sbn: StatusBarNotification) {
        handleSourceRemoved(module, sbn.key)
    }

    private fun hookPlugin(module: XposedModule, loader: ClassLoader) {
        if (!pluginLoaders.add(loader)) return
        runCatching {
            val controller = loader.loadClass(WINDOW_CONTROLLER)
            val sendEvent = controller.declaredMethods.singleOrNull {
                it.name == "sendWindowAnimEvent" &&
                    it.parameterTypes.contentEquals(
                        arrayOf(
                            String::class.java,
                            Boolean::class.javaPrimitiveType,
                            Boolean::class.javaPrimitiveType,
                            Bundle::class.java,
                        ),
                    )
            } ?: error("sendWindowAnimEvent(String, boolean, boolean, Bundle) not found")
            module.hook(sendEvent).intercept { chain ->
                val event = chain.args.getOrNull(0) as? String
                val freeform = chain.args.getOrNull(1) as? Boolean ?: false
                val interrupted = chain.args.getOrNull(2) as? Boolean ?: false
                val request = chain.args.getOrNull(3) as? Bundle
                val packageName = request?.getString(PACKAGE_NAME)
                val result = chain.proceed()
                handleWindowEvent(
                    module = module,
                    controller = chain.thisObject,
                    event = event,
                    packageName = packageName,
                    freeform = freeform,
                    interrupted = interrupted,
                    originalResult = result,
                )
            }

            controller.declaredMethods.filter {
                it.name == "onTopActivityChange" && it.parameterCount == 5 &&
                    it.parameterTypes.firstOrNull() == ComponentName::class.java
            }.forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val packageName = (chain.args.getOrNull(0) as? ComponentName)?.packageName
                    if (session.clearIfForeground(packageName)) {
                        clearPendingTransaction()
                        clearMediaMonitor()
                        handlePrimaryCleared(module, "source_foreground")
                    }
                    removeSecondarySourcesForPackage(module, packageName, "secondary_foreground")
                    result
                }
            }

            controller.declaredMethods.filter {
                it.name == "updateDynamicIslandView" && it.parameterCount == 2
            }.forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val data = chain.args.getOrNull(0)
                    val sourceKey = data?.let(::resolveDataKey)
                    if (data != null && sourceKey != null &&
                        updatingAnchor.get() != true &&
                        sourceKey == session.activeSourceKey()
                    ) {
                        if (isMediaData(data) && !mediaDataIsActive(data)) {
                            if (session.clearIfSource(sourceKey)) {
                                clearPendingTransaction()
                                clearMediaMonitor()
                                handlePrimaryCleared(module, "media_paused_update")
                            }
                        } else {
                            updateAnchorData(module, chain.thisObject, data, "source_update")
                            bindMediaMonitor(module, data, sourceKey)
                        }
                    }
                    result
                }
            }

            val content = loader.loadClass(CONTENT_VIEW)
            content.declaredMethods.filter { it.name == "onIslandClick" && it.parameterCount == 0 }
                .forEach { method ->
                    module.hook(method).intercept { chain ->
                        if (!isAnchorContent(chain.thisObject)) {
                            return@intercept chain.proceed()
                        }
                        val sourceKey = session.activeSourceKey()
                        val packageName = session.activePackageName()
                        if (sourceKey == null || packageName == null) {
                            module.log("HyperBridge: permanent blank anchor click blocked")
                            return@intercept null
                        }
                        val source = findPluginSource(controllerRef.get(), packageName)
                        if (source == null || source.key != sourceKey) {
                            module.log(
                                "HyperBridge: permanent active anchor click source unavailable " +
                                    "pkg=$packageName key=$sourceKey",
                            )
                            return@intercept chain.proceed()
                        }
                        val sourceClick = findMethod(source.contentView.javaClass, "onIslandClick")
                        runCatching {
                            sourceClick.apply { isAccessible = true }.invoke(source.contentView)
                        }.onSuccess {
                            if (session.clearIfSource(sourceKey)) {
                                clearPendingTransaction()
                                clearMediaMonitor()
                                restoreBlankAnchor(module, "source_expand")
                            }
                            module.log(
                                "HyperBridge: permanent active anchor click delegated " +
                                    "pkg=$packageName key=$sourceKey",
                            )
                        }.onFailure {
                            module.log(
                                "HyperBridge: permanent active anchor click delegation failed: " +
                                    it.message,
                            )
                        }.getOrElse {
                            chain.proceed()
                        }
                    }
                }

            val window = loader.loadClass(WINDOW_VIEW)
            window.declaredMethods.filter { it.name == "onLongPress" && it.parameterCount == 3 }
                .forEach { method ->
                    module.hook(method).intercept { chain ->
                        val candidate = chain.args.getOrNull(1) ?: chain.args.getOrNull(0)
                        if (isAnchorContent(candidate) && !session.hasActiveSource()) {
                            module.log("HyperBridge: permanent blank anchor long-press blocked")
                            null
                        } else {
                            chain.proceed()
                        }
                    }
                }

            runCatching {
                val coordinator = loader.loadClass(EVENT_COORDINATOR)
                coordinator.declaredMethods.filter {
                    it.name == "onStateChange" && it.parameterCount == 2 &&
                        it.parameterTypes.firstOrNull() == String::class.java
                }.forEach { method ->
                    module.hook(method).intercept { chain ->
                        val result = chain.proceed()
                        val transition = chain.args.getOrNull(0) as? String
                        val view = chain.args.getOrNull(1)
                        val viewKey = resolveViewKey(view)
                        if (transition in SECONDARY_HIDDEN_TRANSITIONS) {
                            viewKey?.let { key ->
                                removeSecondarySource(module, key, "secondary_$transition")
                            }
                        }
                        result
                    }
                }
            }.onFailure {
                module.log(
                    "HyperBridge: permanent-island secondary lifecycle hook unavailable " +
                        "loader=${loader.hashCode()}: ${it.message}",
                )
            }

            module.log("HyperBridge: permanent-island plugin hooks installed loader=${loader.hashCode()}")
        }.onFailure {
            pluginLoaders.remove(loader)
            module.log("HyperBridge: permanent-island plugin hooks unavailable loader=${loader.hashCode()}: ${it.message}")
        }
    }

    private fun hookFocusRemoval(module: XposedModule, loader: ClassLoader) {
        if (!focusLoaders.add(loader)) return
        runCatching {
            val controller = loader.loadClass(FOCUS_CONTROLLER)
            val methods = controller.declaredMethods.filter {
                it.name == "onNotificationRemoved" &&
                    it.parameterTypes.any { type -> type == StatusBarNotification::class.java }
            }
            if (methods.isEmpty()) error("onNotificationRemoved(StatusBarNotification, ...) not found")
            methods.forEach { method ->
                module.hook(method).intercept { chain ->
                    val removed = chain.args.firstOrNull { it is StatusBarNotification } as? StatusBarNotification
                    val result = chain.proceed()
                    removed?.key?.let { handleSourceRemoved(module, it) }
                    result
                }
            }
        }.onFailure {
            focusLoaders.remove(loader)
        }
    }

    private fun handleWindowEvent(
        module: XposedModule,
        controller: Any?,
        event: String?,
        packageName: String?,
        freeform: Boolean,
        interrupted: Boolean,
        originalResult: Any?,
    ): Any? {
        if (!HookConfig.permanentIslandEnabled()) {
            session.reset()
            clearPendingTransaction()
            clearMediaMonitor()
            secondarySources.clear()
            anchorSuspendedForSecondary = false
            blankAnchorData = null
            return originalResult
        }
        if (packageName.isNullOrBlank()) return originalResult
        controller?.let { controllerRef = WeakReference(it) }
        val resolvedContext = resolveContext(controller) ?: context.get()
        if (resolvedContext != null) context = WeakReference(resolvedContext.applicationContext)

        return when (event) {
            REQUEST_CLOSE_POSITION -> {
                if (freeform || interrupted || originalResult !is Bundle || resolvedContext == null) {
                    originalResult
                } else {
                    val anchorData = findAnchorData(controller)
                    val source = findPluginSource(controller, packageName)
                    val sourceData = source?.data
                    val sourceKey = source?.key
                    val cutout = sourceKey?.let { resolveCutoutRect(controller) }
                    if (anchorData == null || source == null || sourceData == null ||
                        sourceKey == null || cutout == null || !validCutout(cutout)
                    ) {
                        originalResult
                    } else {
                        val currentSource = session.activeSourceKey()
                        if (
                            (currentSource != null && currentSource != sourceKey) ||
                            (currentSource == null && anchorSuspendedForSecondary)
                        ) {
                            // The permanent anchor is ShowOnce/property-0, which makes stock Xiaomi
                            // prefer the physical cutout even when another island should animate
                            // into the right-hand secondary slot. Preserve an already-correct native
                            // target; otherwise derive the slot from Xiaomi's own small-island view
                            // dimensions/resources instead of hard-coding device pixels.
                            secondarySources[sourceKey] = packageName
                            val secondaryTarget = resolveSecondaryTarget(
                                originalResult = originalResult,
                                contentView = source.contentView,
                                cutout = cutout,
                            )
                            module.log(
                                "HyperBridge: permanent-island secondary tracked pkg=$packageName " +
                                    "key=$sourceKey target=" +
                                    (secondaryTarget?.let { "${it.width()}x${it.height()}" } ?: "native"),
                            )
                            if (secondaryTarget == null) {
                                originalResult
                            } else {
                                Bundle(originalResult).apply {
                                    putParcelable(POSITION, Rect(secondaryTarget))
                                }
                            }
                        } else if (currentSource != null) {
                            originalResult
                        } else {
                            // Capture the real blank state once, before any source content is
                            // written to the anchor. Never re-learn it from an adopted update.
                            if (blankAnchorData == null) {
                                blankAnchorData = cloneData(anchorData, forceShowOnce = true)
                            }
                            if (isMediaData(sourceData) && !mediaDataIsActive(sourceData)) {
                                module.log(
                                    "HyperBridge: permanent-island ignored inactive media " +
                                        "pkg=$packageName key=$sourceKey",
                                )
                                return originalResult
                            }
                            val generation = session.requestClose(packageName, sourceKey)
                            pendingSourceData = sourceData
                            pendingGeneration = generation
                            appliedGeneration = -1L
                                bindMediaMonitor(module, sourceData, sourceKey)

                            // request_close_position is the earliest authoritative app-close
                            // callback. Applying here advances the visual update by one native
                            // close phase without an arbitrary timer.
                            if (updateAnchorData(module, controller, sourceData, "close_request")) {
                                appliedGeneration = generation
                                module.log(
                                    "HyperBridge: permanent-island content pre-applied " +
                                        "pkg=$packageName gen=$generation",
                                )
                            }

                            Bundle(originalResult).apply {
                                putParcelable(POSITION, Rect(cutout))
                            }.also {
                                module.log(
                                    "HyperBridge: permanent-island target override pkg=$packageName " +
                                        "gen=$generation rect=${cutout.width()}x${cutout.height()}",
                                )
                            }
                        }
                    }
                }
            }

            CLOSE_APP_START -> {
                session.markStarted(packageName)?.let { generation ->
                    val sourceData = pendingSourceData
                        ?.takeIf { pendingGeneration == generation }
                    if (sourceData != null && updateAnchorData(
                            module,
                            controller,
                            sourceData,
                            "close_start",
                        )
                    ) {
                        appliedGeneration = generation
                        module.log(
                            "HyperBridge: permanent-island content applied early pkg=$packageName " +
                                "gen=$generation",
                        )
                    }
                    module.log("HyperBridge: permanent-island close started pkg=$packageName gen=$generation")
                }
                originalResult
            }

            CLOSE_APP_END -> {
                session.complete(packageName)?.let { showing ->
                    val alreadyApplied = appliedGeneration == showing.generation
                    val fallbackApplied = if (alreadyApplied) {
                        true
                    } else {
                        pendingSourceData
                            ?.takeIf { pendingGeneration == showing.generation }
                            ?.let { updateAnchorData(module, controller, it, "close_end_fallback") }
                            ?: false
                    }
                    if (fallbackApplied) {
                        module.log(
                            "HyperBridge: permanent-island content committed pkg=$packageName " +
                                "gen=${showing.generation}",
                        )
                    } else {
                        session.reset()
                        module.log(
                            "HyperBridge: permanent-island content update skipped; data unavailable " +
                                "pkg=$packageName gen=${showing.generation}",
                        )
                    }
                    clearPendingTransaction()
                }
                originalResult
            }

            APP_TO_RECENT -> {
                if (session.abort(packageName)) {
                    clearPendingTransaction()
                    clearMediaMonitor()
                    handlePrimaryCleared(module, "app_to_recent")
                    module.log("HyperBridge: permanent-island close aborted pkg=$packageName")
                } else {
                    removeSecondarySourcesForPackage(
                        module,
                        packageName,
                        "secondary_app_to_recent",
                    )
                }
                originalResult
            }

            else -> originalResult
        }
    }

    private fun clearPendingTransaction() {
        pendingSourceData = null
        pendingGeneration = -1L
        appliedGeneration = -1L
    }

    private fun handlePrimaryCleared(module: XposedModule, reason: String) {
        if (secondarySources.isNotEmpty() && suspendAnchorForSecondaries(module)) {
            module.log(
                "HyperBridge: permanent-island primary cleared reason=$reason " +
                    "secondaryCount=${secondarySources.size}",
            )
            return
        }
        restoreBlankAnchor(module, reason)
    }

    private fun restoreBlankAnchor(module: XposedModule, reason: String) {
        if (!HookConfig.permanentIslandEnabled() || anchorSuspendedForSecondary) return
        val controller = controllerRef.get() ?: return
        val blank = blankAnchorData
        if (blank == null) {
            // The notification itself still contains the original blank payload. Reposting is a
            // safer recovery than cloning whatever content currently happens to occupy the anchor.
            repostAnchorNotification(module, "missing_blank_$reason")
            return
        }
        if (invokeUpdate(controller, cloneData(blank, forceShowOnce = true))) {
            module.log("HyperBridge: permanent-island blank restored reason=$reason")
        } else {
            repostAnchorNotification(module, "update_failed_$reason")
        }
    }

    private fun handleSourceRemoved(module: XposedModule, sourceKey: String?) {
        if (sourceKey.isNullOrBlank()) return
        if (session.clearIfSource(sourceKey)) {
            clearPendingTransaction()
            clearMediaMonitor()
            handlePrimaryCleared(module, "source_removed")
        }
        removeSecondarySource(module, sourceKey, "secondary_source_removed")
    }

    private fun removeSecondarySourcesForPackage(
        module: XposedModule,
        packageName: String?,
        reason: String,
    ) {
        if (packageName.isNullOrBlank()) return
        secondarySources.entries
            .filter { it.value == packageName }
            .map { it.key }
            .forEach { key -> removeSecondarySource(module, key, reason) }
    }

    private fun removeSecondarySource(module: XposedModule, sourceKey: String, reason: String) {
        if (secondarySources.remove(sourceKey) == null) return
        module.log(
            "HyperBridge: permanent-island secondary cleared reason=$reason " +
                "remaining=${secondarySources.size}",
        )
        if (anchorSuspendedForSecondary && secondarySources.isEmpty()) {
            anchorSuspendedForSecondary = false
            repostAnchorNotification(module, "secondary_finished_$reason")
        }
    }

    private fun suspendAnchorForSecondaries(module: XposedModule): Boolean {
        if (anchorSuspendedForSecondary) return true
        if (!ActiveIslandDismissHook.isActive()) return false
        val controller = controllerRef.get() ?: return false
        val anchorKey = findAnchorData(controller)?.let(::resolveDataKey)
            ?: blankAnchorData?.let(::resolveDataKey)
            ?: return false
        anchorSuspendedForSecondary = true
        ActiveIslandDismissHook.dismissKey(anchorKey)
        module.log("HyperBridge: permanent-island anchor hidden for secondary promotion")
        return true
    }

    private fun repostAnchorNotification(module: XposedModule, reason: String): Boolean {
        val ctx = context.get() ?: return false
        val manager = ctx.getSystemService(NotificationManager::class.java) ?: return false
        val anchor = manager.activeNotifications.orEmpty().firstOrNull { sbn ->
            sbn.id == IslandProtocol.PERMANENT_ANCHOR_ID &&
                sbn.notification.extras.getBoolean(
                    IslandProtocol.EXTRA_PERMANENT_ANCHOR,
                    false,
                )
        } ?: return false
        return runCatching {
            manager.notify(anchor.tag, anchor.id, anchor.notification)
            module.log("HyperBridge: permanent-island anchor reposted reason=$reason")
            true
        }.getOrDefault(false)
    }

    private fun resolveViewKey(view: Any?): String? {
        view ?: return null
        return invokeNoArg(view, "getCurrentIslandData")?.let(::resolveDataKey)
            ?: (invokeNoArg(view, "getIslandKey") as? String)
    }

    private fun findPluginSource(controller: Any?, packageName: String): PluginSource? {
        val view = controller?.let { invokeNoArg(it, "getView") } ?: return null
        val request = findMethod(
            type = view.javaClass,
            name = "requestHasIsland",
            parameterTypes = arrayOf(String::class.java),
        ) ?: return null
        val islandViews = runCatching {
            request.apply { isAccessible = true }.invoke(view, packageName) as? List<*>
        }.getOrNull().orEmpty()
        return islandViews.asReversed().firstNotNullOfOrNull { islandView ->
            val contentView = islandView ?: return@firstNotNullOfOrNull null
            val data = invokeNoArg(contentView, "getCurrentIslandData")
                ?: return@firstNotNullOfOrNull null
            val key = resolveDataKey(data) ?: return@firstNotNullOfOrNull null
            if (isAnchorKey(key)) null else PluginSource(contentView, data, key)
        }
    }

    private fun resolveSecondaryTarget(
        originalResult: Bundle,
        contentView: Any,
        cutout: Rect,
    ): Rect? {
        val native = originalResult.getParcelable(POSITION, Rect::class.java)
        if (native != null && validCutout(native) && native.centerX() > cutout.right) {
            return null
        }

        val smallView = invokeNoArg(contentView, "getSmallIslandView") as? View
        val measured = smallView?.let(::viewRectOnScreen)
        if (measured != null && validCutout(measured) && measured.centerX() > cutout.right) {
            return measured
        }

        val resources = smallView?.resources ?: (contentView as? View)?.resources ?: return null
        val packageNames = buildList {
            sequenceOf(smallView, contentView as? View).filterNotNull().forEach { view ->
                if (view.id != View.NO_ID) {
                    runCatching { resources.getResourcePackageName(view.id) }
                        .getOrNull()
                        ?.let { packageName -> add(packageName) }
                }
            }
            add("miui.systemui.plugin")
            add("com.android.systemui")
        }.distinct()
        val gap = resolveDimension(resources, "island_space", packageNames) ?: 0
        val width = smallView?.width?.takeIf { it > 0 }
            ?: resolveDimension(resources, "small_island_width", packageNames)
            ?: cutout.width()
        val height = smallView?.height?.takeIf { it > 0 }
            ?: resolveDimension(resources, "small_island_height", packageNames)
            ?: cutout.height()
        if (width <= 0 || height <= 0) return null

        val left = cutout.right + gap
        val top = cutout.centerY() - height / 2
        val derived = Rect(left, top, left + width, top + height)
        return derived.takeIf {
            validCutout(it) && it.left >= cutout.right &&
                it.right <= resources.displayMetrics.widthPixels
        }
    }

    private fun bindMediaMonitor(module: XposedModule, data: Any, sourceKey: String) {
        val sbn = resolveSourceSbn(data) ?: run {
            clearMediaMonitor()
            return
        }
        if (!isMediaNotification(sbn.notification)) {
            clearMediaMonitor()
            return
        }
        val token = sbn.notification.extras
            ?.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
            ?: run {
                clearMediaMonitor()
                return
            }
        val ctx = context.get() ?: return
        if (mediaSourceKey == sourceKey && mediaController?.sessionToken == token) return

        clearMediaMonitor()
        val controller = runCatching { MediaController(ctx, token) }.getOrNull() ?: return
        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                if (mediaSourceKey != sourceKey) return
                if (isPlaybackActive(state)) return
                if (session.clearIfSource(sourceKey)) {
                    clearPendingTransaction()
                    clearMediaMonitor()
                    handlePrimaryCleared(module, "media_paused")
                    module.log(
                        "HyperBridge: permanent media cleared on playback stop key=$sourceKey " +
                            "state=${state?.state}",
                    )
                }
            }

            override fun onSessionDestroyed() {
                if (mediaSourceKey != sourceKey) return
                if (session.clearIfSource(sourceKey)) {
                    clearPendingTransaction()
                    clearMediaMonitor()
                    handlePrimaryCleared(module, "media_session_destroyed")
                }
            }
        }
        mediaController = controller
        mediaCallback = callback
        mediaSourceKey = sourceKey
        runCatching { controller.registerCallback(callback, mainHandler) }
            .onFailure {
                clearMediaMonitor()
                module.log("HyperBridge: permanent media monitor unavailable: ${it.message}")
            }
        val initial = runCatching { controller.playbackState }.getOrNull()
        if (initial != null && !isPlaybackActive(initial) && session.clearIfSource(sourceKey)) {
            clearPendingTransaction()
            clearMediaMonitor()
            handlePrimaryCleared(module, "media_inactive_initial")
        }
    }

    private fun clearMediaMonitor() {
        val controller = mediaController
        val callback = mediaCallback
        mediaController = null
        mediaCallback = null
        mediaSourceKey = null
        if (controller != null && callback != null) {
            runCatching { controller.unregisterCallback(callback) }
        }
    }

    private fun isMediaData(data: Any): Boolean =
        resolveSourceSbn(data)?.notification?.let(::isMediaNotification) == true

    private fun mediaDataIsActive(data: Any): Boolean {
        val sbn = resolveSourceSbn(data) ?: return true
        if (!isMediaNotification(sbn.notification)) return true
        val token = sbn.notification.extras
            ?.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
            ?: return true
        val ctx = context.get() ?: return true
        val state = runCatching { MediaController(ctx, token).playbackState }.getOrNull()
        return state == null || isPlaybackActive(state)
    }

    private fun resolveSourceSbn(data: Any?): StatusBarNotification? {
        val extras = data?.let { invokeNoArg(it, "getExtras") as? Bundle } ?: return null
        return extras.getParcelable(IslandProtocol.MIUI_SBN, StatusBarNotification::class.java)
    }

    private fun isMediaNotification(notification: Notification): Boolean {
        if (notification.category == Notification.CATEGORY_TRANSPORT) return true
        val extras = notification.extras ?: return false
        if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return true
        return extras.getString(Notification.EXTRA_TEMPLATE).orEmpty().contains("MediaStyle")
    }

    private fun isPlaybackActive(state: PlaybackState?): Boolean = when (state?.state) {
        PlaybackState.STATE_PLAYING,
        PlaybackState.STATE_BUFFERING,
        PlaybackState.STATE_CONNECTING,
        PlaybackState.STATE_FAST_FORWARDING,
        PlaybackState.STATE_REWINDING,
        PlaybackState.STATE_SKIPPING_TO_NEXT,
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> true
        else -> false
    }

    private fun resolveDimension(
        resources: android.content.res.Resources,
        name: String,
        packageNames: List<String>,
    ): Int? {
        packageNames.forEach { packageName ->
            val id = resources.getIdentifier(name, "dimen", packageName)
            if (id != 0) {
                runCatching { resources.getDimensionPixelSize(id) }.getOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun viewRectOnScreen(view: View): Rect? {
        if (view.width <= 0 || view.height <= 0) return null
        val location = IntArray(2)
        runCatching { view.getLocationOnScreen(location) }.getOrNull() ?: return null
        return Rect(
            location[0],
            location[1],
            location[0] + view.width,
            location[1] + view.height,
        )
    }

    private fun findAnchorData(controller: Any?): Any? {
        val view = controller?.let { invokeNoArg(it, "getView") } ?: return null
        val contentViews = invokeNoArg(view, "getContentViewList") as? List<*> ?: return null
        return contentViews.asSequence().mapNotNull { contentView ->
            contentView?.let { invokeNoArg(it, "getCurrentIslandData") }
        }.firstOrNull { data -> isAnchorKey(resolveDataKey(data)) }
    }

    private fun updateAnchorData(
        module: XposedModule,
        controller: Any?,
        sourceData: Any,
        reason: String,
    ): Boolean {
        val target = controller ?: controllerRef.get() ?: return false
        val anchorData = findAnchorData(target) ?: blankAnchorData ?: return false
        val anchorKey = resolveDataKey(anchorData) ?: return false
        val adopted = cloneData(sourceData, key = anchorKey, forceShowOnce = true)
        return invokeUpdate(target, adopted).also { updated ->
            if (updated) {
                module.log("HyperBridge: permanent-island anchor updated in place reason=$reason")
            }
        }
    }

    private fun invokeUpdate(controller: Any, data: Any): Boolean {
        if (updatingAnchor.get() == true) return false
        val method = findMethod(
            controller.javaClass,
            "updateDynamicIslandView",
            arrayOf(data.javaClass, Boolean::class.javaPrimitiveType!!),
        ) ?: return false
        return runCatching {
            updatingAnchor.set(true)
            method.apply { isAccessible = true }.invoke(controller, data, false)
            true
        }.getOrDefault(false).also { updatingAnchor.set(false) }
    }

    private fun cloneData(
        source: Any,
        key: String? = resolveDataKey(source),
        forceShowOnce: Boolean,
    ): Any {
        val copy = source.javaClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        val ticker = invokeNoArg(source, "getTickerData") as? String
        val patchedTicker = if (forceShowOnce) patchShowOnceTicker(ticker) else ticker
        val extras = (invokeNoArg(source, "getExtras") as? Bundle)?.let(::Bundle) ?: Bundle()
        key?.let { extras.putString("miui.key", it) }
        extras.putBoolean(IslandProtocol.EXTRA_PERMANENT_ANCHOR, true)
        invokeSetter(copy, "setKey", key)
        invokeSetter(copy, "setTickerData", patchedTicker)
        invokeSetter(copy, "setExtras", extras)
        invokeSetter(copy, "setPriority", invokeNoArg(source, "getPriority"))
        invokeSetter(copy, "setProperties", if (forceShowOnce) 0 else invokeNoArg(source, "getProperties"))
        return copy
    }

    private fun patchShowOnceTicker(ticker: String?): String? {
        if (ticker.isNullOrBlank()) return ticker
        return runCatching {
            JSONObject(ticker)
                .put("islandProperty", 0)
                .put("islandTimeout", Int.MAX_VALUE)
                .put("dismissIsland", false)
                .toString()
        }.getOrDefault(ticker)
    }

    private fun invokeSetter(target: Any, name: String, value: Any?) {
        var current: Class<*>? = target.javaClass
        while (current != null) {
            current.declaredMethods.firstOrNull { it.name == name && it.parameterCount == 1 }?.let {
                runCatching { it.apply { isAccessible = true }.invoke(target, value) }
                return
            }
            current = current.superclass
        }
    }

    private fun resolveDataKey(data: Any?): String? = data?.let { invokeNoArg(it, "getKey") as? String }

    private fun isAnchorContent(contentView: Any?): Boolean {
        val data = contentView?.let { invokeNoArg(it, "getCurrentIslandData") }
        return isAnchorKey(resolveDataKey(data))
    }

    private fun isAnchorKey(key: String?): Boolean =
        key?.contains("|${IslandProtocol.PERMANENT_ANCHOR_ID}|") == true

    private fun resolveCutoutRect(controller: Any?): Rect? {
        val target = controller ?: return null
        val view = invokeNoArg(target, "getView") ?: return null
        return (invokeNoArg(view, "getCutoutRect") as? Rect)?.let(::Rect)
    }

    private fun resolveContext(controller: Any?): Context? {
        val systemUiApplication = runCatching {
            val activityThread = Class.forName("android.app.ActivityThread")
            activityThread.getDeclaredMethod("currentApplication").invoke(null) as? Context
        }.getOrNull()?.takeIf { it.packageName == "com.android.systemui" }
        if (systemUiApplication != null) return systemUiApplication
        val view = controller?.let { invokeNoArg(it, "getView") }
        return (view as? View)?.context ?: (invokeNoArg(view, "getContext") as? Context)
    }

    private fun invokeNoArg(target: Any?, name: String): Any? {
        target ?: return null
        return runCatching {
            findMethod(target.javaClass, name).apply { isAccessible = true }.invoke(target)
        }.getOrNull()
    }

    private fun findMethod(type: Class<*>, name: String): Method {
        var current: Class<*>? = type
        while (current != null) {
            current.declaredMethods.firstOrNull { it.name == name && it.parameterCount == 0 }?.let { return it }
            current = current.superclass
        }
        error("$name() not found on ${type.name}")
    }

    private fun findMethod(type: Class<*>, name: String, parameterTypes: Array<Class<*>>): Method? {
        var current: Class<*>? = type
        while (current != null) {
            current.declaredMethods.firstOrNull {
                it.name == name && it.parameterTypes.contentEquals(parameterTypes)
            }?.let { return it }
            current = current.superclass
        }
        return null
    }

    private fun validCutout(rect: Rect): Boolean =
        !rect.isEmpty && rect.width() in 16..512 && rect.height() in 16..512
}
