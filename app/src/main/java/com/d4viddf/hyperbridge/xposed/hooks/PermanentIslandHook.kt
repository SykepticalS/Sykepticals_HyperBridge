package com.d4viddf.hyperbridge.xposed.hooks

import android.content.ComponentName
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
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

    private const val REQUEST_CLOSE_POSITION = "request_close_position"
    private const val CLOSE_APP_START = "close_app_start"
    private const val CLOSE_APP_END = "close_app_end"
    private const val APP_TO_RECENT = "app_to_recent"
    private const val POSITION = "position"
    private const val PACKAGE_NAME = "packageName"

    private val pluginLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>()),
    )
    private val focusLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>()),
    )
    private val session = PermanentIslandSession()
    @Volatile private var context = WeakReference<Context>(null)
    @Volatile private var controllerRef = WeakReference<Any>(null)
    @Volatile private var blankAnchorData = WeakReference<Any>(null)
    @Volatile private var pendingSourceData = WeakReference<Any>(null)
    @Volatile private var pendingGeneration = -1L
    @Volatile private var appliedGeneration = -1L
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
        if (session.clearIfSource(sbn.key)) {
            restoreBlankAnchor(module, "source_removed")
        }
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
                        restoreBlankAnchor(module, "source_foreground")
                    }
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
                    if (data != null && updatingAnchor.get() != true &&
                        sourceKey == session.adoptedSourceKey()
                    ) {
                        updateAnchorData(module, chain.thisObject, data, "source_update")
                    }
                    result
                }
            }

            val content = loader.loadClass(CONTENT_VIEW)
            content.declaredMethods.filter { it.name == "onIslandClick" && it.parameterCount == 0 }
                .forEach { method ->
                    module.hook(method).intercept { chain ->
                        if (isAnchorContent(chain.thisObject)) {
                            if (session.adoptedSourceKey() == null) {
                                module.log("HyperBridge: permanent blank anchor click blocked")
                                null
                            } else {
                                val result = chain.proceed()
                                session.reset()
                                restoreBlankAnchor(module, "source_expand")
                                result
                            }
                        } else {
                            chain.proceed()
                        }
                    }
                }

            val window = loader.loadClass(WINDOW_VIEW)
            window.declaredMethods.filter { it.name == "onLongPress" && it.parameterCount == 3 }
                .forEach { method ->
                    module.hook(method).intercept { chain ->
                        val candidate = chain.args.getOrNull(1) ?: chain.args.getOrNull(0)
                        if (isAnchorContent(candidate)) {
                            module.log("HyperBridge: permanent anchor long-press blocked")
                            null
                        } else {
                            chain.proceed()
                        }
                    }
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
                    if (session.clearIfSource(removed?.key)) {
                        restoreBlankAnchor(module, "source_removed")
                    }
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
            if (session.reset()) restoreBlankAnchor(module, "disabled")
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
                    val sourceData = findPluginSourceData(controller, packageName)
                    val sourceKey = sourceData?.let(::resolveDataKey)
                    val cutout = sourceKey?.let { resolveCutoutRect(controller) }
                    if (anchorData == null || sourceData == null || sourceKey == null ||
                        cutout == null || !validCutout(cutout)
                    ) {
                        originalResult
                    } else {
                        blankAnchorData = WeakReference(cloneData(anchorData, forceShowOnce = true))
                        val generation = session.requestClose(packageName, sourceKey)
                        pendingSourceData = WeakReference(sourceData)
                        pendingGeneration = generation
                        appliedGeneration = -1L
                        Bundle(originalResult).apply { putParcelable(POSITION, Rect(cutout)) }.also {
                            module.log(
                                "HyperBridge: permanent-island target override pkg=$packageName " +
                                    "gen=$generation rect=${cutout.width()}x${cutout.height()}",
                            )
                        }
                    }
                }
            }

            CLOSE_APP_START -> {
                session.markStarted(packageName)?.let { generation ->
                    val sourceData = pendingSourceData.get()
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
                        pendingSourceData.get()
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
                    pendingSourceData.clear()
                    pendingGeneration = -1L
                    appliedGeneration = -1L
                }
                originalResult
            }

            APP_TO_RECENT -> {
                if (session.abort(packageName)) {
                    pendingSourceData.clear()
                    pendingGeneration = -1L
                    appliedGeneration = -1L
                    restoreBlankAnchor(module, "app_to_recent")
                    module.log("HyperBridge: permanent-island close aborted pkg=$packageName")
                }
                originalResult
            }

            else -> originalResult
        }
    }

    private fun restoreBlankAnchor(module: XposedModule, reason: String) {
        if (!HookConfig.permanentIslandEnabled()) return
        val controller = controllerRef.get() ?: return
        val blank = blankAnchorData.get() ?: findAnchorData(controller)?.also {
            blankAnchorData = WeakReference(cloneData(it, forceShowOnce = true))
        } ?: return
        if (invokeUpdate(controller, cloneData(blank, forceShowOnce = true))) {
            module.log("HyperBridge: permanent-island blank restored reason=$reason")
        }
    }

    private fun findPluginSourceData(controller: Any?, packageName: String): Any? {
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
            islandView?.let { invokeNoArg(it, "getCurrentIslandData") }
        }
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
        val anchorData = findAnchorData(target) ?: blankAnchorData.get() ?: return false
        if (blankAnchorData.get() == null) {
            blankAnchorData = WeakReference(cloneData(anchorData, forceShowOnce = true))
        }
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
