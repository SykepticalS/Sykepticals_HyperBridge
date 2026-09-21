package com.d4viddf.hyperbridge.xposed.hooks

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.d4viddf.hyperbridge.models.IslandWindowImePolicy
import com.d4viddf.hyperbridge.ui.InlineReplyActivity
import com.d4viddf.hyperbridge.ui.InlineReplyIntents
import com.d4viddf.hyperbridge.xposed.dispatch.SystemUiDispatcher
import com.d4viddf.hyperbridge.xposed.log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

/**
 * Shows the reply composer inside the expanded island instead of launching a
 * bottom-of-screen HyperBridge activity.
 */
object IslandInlineReplyHook {
    private const val CONTROLLER =
        "com.android.systemui.statusbar.notification.DynamicIslandController"
    private val stayExpandedCallbacks = setOf(
        "onDynamicPluginCallback_expandedToSmall",
        "onDynamicPluginCallback_bigToSmall",
        "onDynamicPluginCallback_expandedToBig",
    )
    private const val DROP_DOWN = "onDynamicPluginCallback_dropDownExpandedIsland"
    private const val EXPANDED_TO_BIG = "onDynamicPluginCallback_expandedToBig"
    private val loaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>()),
    )
    private val dispatchers = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>()),
    )
    @Volatile private var sendHooked = false

    fun install(module: XposedModule, param: PackageLoadedParam) {
        hookPendingIntentSend(module, param.defaultClassLoader)
        hookDispatchers(module, param.defaultClassLoader)
        hook(module, param.defaultClassLoader)
        DynamicClassLoaderHooks.observe(module, param.defaultClassLoader) { loader ->
            hookDispatchers(module, loader)
            hook(module, loader)
        }
    }

    private val handling = ThreadLocal<Boolean>()

    private fun hookPendingIntentSend(module: XposedModule, loader: ClassLoader) {
        if (sendHooked) return
        sendHooked = true
        runCatching {
            val clazz = loader.loadClass("android.app.PendingIntent")
            val methods = clazz.declaredMethods.filter {
                it.name == "send" || it.name == "sendAndReturnResult"
            }
            check(methods.isNotEmpty()) { "PendingIntent.send missing" }
            methods.forEach { method ->
                module.hook(method).intercept { chain ->
                    if (openReply(chain.thisObject, chain.args, module)) null else chain.proceed()
                }
            }
            module.log("HyperBridge: island inline-reply PendingIntent send hooked count=${methods.size}")
            Log.i("HyperBridge", "island inline-reply PendingIntent send hooked count=${methods.size}")
        }.onFailure {
            sendHooked = false
            module.log("HyperBridge: island inline-reply send hook unavailable: ${it.message}")
        }
    }

    private fun hookDispatchers(module: XposedModule, loader: ClassLoader) {
        if (!dispatchers.add(loader)) return
        val names = arrayOf(
            "miui.systemui.notification.focus.FocusNotifPreHandler\$ClickHandler",
            "miui.systemui.notification.focus.FocusNotifPreHandler",
            "miui.systemui.notification.focus.FocusNotifUtils",
            "miui.systemui.dynamicisland.event.ClickEventCoordinator",
            "miui.systemui.notification.focus.moduleV3.ModuleTextButtonViewHolder",
            "miui.systemui.notification.focus.moduleV3.ModuleTextButton4ViewHolder",
            "miui.systemui.notification.focus.moduleV3.ModuleTextButton5ViewHolder",
            "miui.systemui.notification.focus.moduleV3.ModuleDecoPortTextButtonViewHolder",
            "miui.systemui.dynamicisland.anim.DynamicIslandAnimationDelegate",
        )
        names.forEach { name ->
            runCatching {
                val clazz = loader.loadClass(name)
                val collapseClass = name.contains("AnimationDelegate")
                val methods = clazz.declaredMethods.filter { method ->
                    if (collapseClass) {
                        holdTimer(method)
                    } else {
                        method.parameterTypes.any { PendingIntent::class.java.isAssignableFrom(it) } ||
                            method.name.contains("startPendingIntent", ignoreCase = true) ||
                            method.name.contains("clickWithCollapse", ignoreCase = true) ||
                            method.name.contains("onClick", ignoreCase = true) ||
                            method.name.contains("ExpandedTime", ignoreCase = true) ||
                            method.name.contains("TimeoutMs", ignoreCase = true)
                    }
                }
                methods.forEach { method ->
                    module.hook(method).intercept { chain ->
                        if (holdTimer(method) && IslandReplyComposer.shouldStayExpanded()) {
                            Log.i("HyperBridge", "island reply held ${clazz.simpleName}.${method.name}")
                            return@intercept heldResult(method)
                        }
                        if (collapseClass) return@intercept chain.proceed()
                        val collapseMethod = method.name.contains("collapse", ignoreCase = true) ||
                            method.name.contains("toSmall", ignoreCase = true)
                        if (openReply(chain.thisObject, chain.args, module)) {
                            null
                        } else if (collapseMethod && IslandReplyComposer.shouldStayExpanded()) {
                            null
                        } else {
                            chain.proceed()
                        }
                    }
                }
                if (methods.isNotEmpty()) {
                    module.log("HyperBridge: island reply hooked ${clazz.simpleName} methods=${methods.size}")
                    Log.i("HyperBridge", "island reply hooked ${clazz.simpleName} methods=${methods.size}")
                }
            }
        }
    }

    private fun openReply(thisObject: Any?, args: List<Any?>?, module: XposedModule): Boolean {
        if (handling.get() == true) return false
        val pendingIntent = (thisObject as? PendingIntent)
            ?: args?.firstOrNull { it is PendingIntent } as? PendingIntent
            ?: return false
        val payload = IslandReplyComposer.payloadFrom(pendingIntent) ?: return false
        val context = args?.firstOrNull { it is Context } as? Context
            ?: (thisObject as? View)?.context
            ?: (thisObject as? Context)
        val source = args?.firstOrNull { it is View } ?: thisObject
        IslandReplyComposer.markOpening()
        handling.set(true)
        return try {
            IslandReplyComposer.openNow(context, payload, module, source)
            Log.i("HyperBridge", "island reply intercept swallowed activity fallback")
            true
        } finally {
            handling.set(false)
        }
    }

    private fun holdTimer(method: java.lang.reflect.Method): Boolean {
        val name = method.name
        if (name.contains("dropDown", ignoreCase = true)) return false
        return name.contains("ToSmall", ignoreCase = true) ||
            name.contains("resetToSmall", ignoreCase = true) ||
            name.contains("expandedToBig", ignoreCase = true) ||
            name.contains("ExpandedTime", ignoreCase = true) ||
            name.contains("TimeoutMs", ignoreCase = true) ||
            name.contains("timeout", ignoreCase = true) && name.startsWith("get")
    }

    private fun heldResult(method: java.lang.reflect.Method): Any? = when (method.returnType) {
        java.lang.Boolean.TYPE -> java.lang.Boolean.FALSE
        java.lang.Integer.TYPE -> Int.MAX_VALUE / 4
        java.lang.Long.TYPE -> 24 * 60 * 60 * 1000L
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        else -> null
    }

    private fun hook(module: XposedModule, loader: ClassLoader) {
        if (!loaders.add(loader)) return
        runCatching {
            val controller = loader.loadClass(CONTROLLER)
            val callback = findCallback(controller)
            module.hook(callback).intercept { chain ->
                val name = chain.args.getOrNull(0) as? String
                if (name == DROP_DOWN) {
                    IslandReplyComposer.dismiss()
                    return@intercept chain.proceed()
                }
                if (name in stayExpandedCallbacks && IslandReplyComposer.shouldStayExpanded()) {
                    Log.i("HyperBridge", "island reply holding expanded, skipped $name")
                    return@intercept null
                }
                val result = chain.proceed()
                if (name == EXPANDED_TO_BIG) IslandReplyComposer.ensureAttached()
                result
            }
        }.onFailure {
            loaders.remove(loader)
        }
    }

    private fun findCallback(controller: Class<*>): java.lang.reflect.Method {
        var current: Class<*>? = controller
        while (current != null) {
            runCatching {
                return current.getDeclaredMethod(
                    "onDynamicPluginCallback",
                    String::class.java,
                    Bundle::class.java,
                )
            }
            current = current.superclass
        }
        error("onDynamicPluginCallback(String, Bundle) not found")
    }
}

internal data class IslandReplyPayload(
    val replyAction: PendingIntent,
    val resultKey: String,
    val sourcePackage: String?,
)

internal object IslandReplyComposer {
    private const val TAG = "hyperbridge.island_reply"
    private val main = Handler(Looper.getMainLooper())
    private var overlay = WeakReference<View>(null)
    private var imeFlags: Pair<Int, Int>? = null
    @Volatile private var intendedOpen = false
    private var lastPayload: IslandReplyPayload? = null
    private var lastModule: XposedModule? = null
    private var lastContext = WeakReference<Context?>(null)
    private var lastSource = WeakReference<Any?>(null)
    private var hiddenButtons: List<View> = emptyList()

    fun openNow(
        context: Context?,
        payload: IslandReplyPayload,
        module: XposedModule,
        source: Any? = null,
    ): Boolean {
        lastPayload = payload
        lastModule = module
        lastContext = WeakReference(context)
        lastSource = WeakReference(source)
        intendedOpen = true
        MarqueeHook.holdAutoHide()
        SystemUiDispatcher.notifyReplyComposer(true)
        val run = {
            runCatching { embed(payload, module, source) }
                .onFailure { module.log("HyperBridge: island reply embed failed: ${it.message}") }
                .getOrDefault(false)
        }
        return if (Looper.myLooper() == Looper.getMainLooper()) {
            run() || scheduleRetry(payload, module, source)
        } else {
            var shown = false
            val posted = java.util.concurrent.CountDownLatch(1)
            main.postAtFrontOfQueue {
                shown = run() || scheduleRetry(payload, module, source)
                posted.countDown()
            }
            posted.await(400, java.util.concurrent.TimeUnit.MILLISECONDS)
            shown || intendedOpen
        }
    }

    fun open(context: Context?, payload: IslandReplyPayload, module: XposedModule) {
        openNow(context, payload, module)
    }

    private fun scheduleRetry(
        payload: IslandReplyPayload,
        module: XposedModule,
        source: Any?,
    ): Boolean {
        main.postDelayed({
            if (!intendedOpen) return@postDelayed
            if (overlay.get()?.isAttachedToWindow == true) return@postDelayed
            runCatching { embed(payload, module, source) }
        }, 50)
        main.postDelayed({
            if (!intendedOpen) return@postDelayed
            if (overlay.get()?.isAttachedToWindow == true) return@postDelayed
            runCatching { embed(payload, module, source) }
        }, 160)
        return true
    }

    fun isOpen(): Boolean = intendedOpen

    fun markOpening() {
        intendedOpen = true
        MarqueeHook.holdAutoHide()
    }

    fun markAborted() {
        if (overlay.get()?.isAttachedToWindow == true) return
        intendedOpen = false
    }

    fun shouldStayExpanded(): Boolean = intendedOpen

    fun ensureAttached() {
        if (!intendedOpen) return
        val view = overlay.get()
        if (view?.isAttachedToWindow == true && view.isShown) return
        val payload = lastPayload ?: return
        val module = lastModule ?: return
        val run = {
            runCatching { embed(payload, module, lastSource.get()) }
            Unit
        }
        if (Looper.myLooper() == Looper.getMainLooper()) run() else main.post(run)
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { dismiss() }
            return
        }
        intendedOpen = false
        restoreHiddenButtons()
        val view = overlay.get()
        overlay = WeakReference(null)
        if (view != null) {
            hideIme(view)
            (view.parent as? ViewGroup)?.removeView(view)
            restoreImeWindow(view)
        }
        MarqueeHook.releaseAutoHide()
        SystemUiDispatcher.notifyReplyComposer(false)
    }

    private fun dismissKeepingIntent() {
        restoreHiddenButtons()
        val view = overlay.get() ?: return
        overlay = WeakReference(null)
        hideIme(view)
        (view.parent as? ViewGroup)?.removeView(view)
        restoreImeWindow(view)
    }

    private fun restoreHiddenButtons() {
        hiddenButtons.forEach { button ->
            button.visibility = View.VISIBLE
        }
        hiddenButtons = emptyList()
    }

    fun payloadFrom(pendingIntent: PendingIntent): IslandReplyPayload? {
        val intent = pendingIntentIntent(pendingIntent) ?: return null
        if (!intent.getBooleanExtra(InlineReplyIntents.EXTRA_INLINE_REPLY, false) &&
            intent.action != InlineReplyIntents.ACTION &&
            intent.component?.className?.contains("InlineReply") != true
        ) {
            return null
        }
        val replyAction = parcelablePendingIntent(intent, InlineReplyActivity.EXTRA_PENDING_INTENT)
            ?: return null
        val resultKey = intent.getStringExtra(InlineReplyActivity.EXTRA_RESULT_KEY)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return IslandReplyPayload(
            replyAction = replyAction,
            resultKey = resultKey,
            sourcePackage = intent.getStringExtra(InlineReplyActivity.EXTRA_PACKAGE_NAME),
        )
    }

    private fun embed(
        payload: IslandReplyPayload,
        module: XposedModule,
        source: Any?,
    ): Boolean {
        val row = findButtonRow(source)?.takeIf { host ->
            host.javaClass.simpleName.contains("Window", ignoreCase = true).not() &&
                host.childCount <= 6
        } ?: return false
        val existing = overlay.get()
        if (existing?.parent === row && existing.isAttachedToWindow) return true
        dismissKeepingIntent()
        val composer = buildComposer(row.context, payload, module)
        hiddenButtons = (0 until row.childCount).map { row.getChildAt(it) }.filter { it !== composer }
        hiddenButtons.forEach { it.visibility = View.GONE }
        val params = when (row) {
            is LinearLayout -> LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            )
            is FrameLayout -> FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            )
            else -> ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        row.addView(composer, params)
        overlay = WeakReference(composer)
        intendedOpen = true
        IslandWindowImeHook.sanitize(row)
        composer.post {
            val field = composer.findViewWithTag<EditText>("$TAG.field") ?: return@post
            prepareImeWindow(field)
            field.requestFocus()
            field.context.getSystemService(InputMethodManager::class.java)
                ?.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
        }
        module.log("HyperBridge: island reply embedded in ${row.javaClass.simpleName} children=${row.childCount}")
        Log.i("HyperBridge", "island reply embedded in ${row.javaClass.simpleName}")
        return true
    }

    private fun findButtonRow(source: Any?): ViewGroup? {
        viewFrom(source)?.let { view ->
            buttonRowAround(view)?.let { return it }
        }
        val expanded = findNamedInWindows("DynamicIslandExpandedView") as? ViewGroup ?: return null
        val clickable = mutableListOf<TextView>()
        collectClickableTexts(expanded, clickable)
        val reply = clickable.firstOrNull { text ->
            text.text?.toString()?.contains("Reply", ignoreCase = true) == true
        }?.parent as? ViewGroup
        if (reply != null) return reply
        return clickable.mapNotNull { it.parent as? ViewGroup }
            .firstOrNull { parent ->
                parent.childCount in 1..4 && clickable.count { it.parent === parent } >= 1
            } ?: clickable.lastOrNull()?.parent as? ViewGroup
            ?: bottomRow(expanded)
    }

    private fun bottomRow(root: ViewGroup): ViewGroup? {
        var best: ViewGroup? = null
        fun walk(view: View) {
            if (view is ViewGroup &&
                view.childCount in 1..4 &&
                view !== root &&
                view.javaClass.simpleName.contains("Window", ignoreCase = true).not()
            ) {
                val texts = (0 until view.childCount).count { view.getChildAt(it) is TextView }
                if (texts >= 1) best = view
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) walk(view.getChildAt(index))
            }
        }
        walk(root)
        return best
    }

    private fun buttonRowAround(view: View): ViewGroup? {
        var current: View? = view
        repeat(6) {
            val parent = current?.parent as? ViewGroup ?: return@repeat
            if (parent.childCount in 2..4) {
                val clickable = (0 until parent.childCount).count { index ->
                    val child = parent.getChildAt(index)
                    child.isClickable || child is TextView || child is ViewGroup
                }
                if (clickable >= 1 && parent.javaClass.simpleName.contains("Window", ignoreCase = true).not()) {
                    return parent
                }
            }
            current = parent
        }
        return view.parent as? ViewGroup
    }

    private fun viewFrom(source: Any?): View? {
        when (source) {
            is View -> return source
            null -> return null
        }
        val clazz = source!!.javaClass
        val names = arrayOf("itemView", "view", "mView", "rootView", "binding")
        for (name in names) {
            var current: Class<*>? = clazz
            while (current != null) {
                val field = runCatching { current!!.getDeclaredField(name).apply { isAccessible = true } }.getOrNull()
                if (field != null) {
                    when (val value = runCatching { field.get(source) }.getOrNull()) {
                        is View -> return value
                    }
                }
                current = current.superclass
            }
        }
        return clazz.declaredFields.firstNotNullOfOrNull { field ->
            field.isAccessible = true
            runCatching { field.get(source) as? View }.getOrNull()
        }
    }

    private fun collectClickableTexts(view: View, out: MutableList<TextView>) {
        if (view is TextView && (view.isClickable || view.parent is ViewGroup)) {
            val text = view.text?.toString().orEmpty()
            if (text.isNotBlank()) out += view
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) collectClickableTexts(view.getChildAt(index), out)
        }
    }

    private fun findNamedInWindows(simpleName: String): View? {
        windowRoots().forEach { root ->
            findNamed(root, simpleName)?.let { return it }
        }
        return null
    }

    private fun buildComposer(
        context: Context,
        payload: IslandReplyPayload,
        module: XposedModule,
    ): View {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val field = EditText(context).apply {
            tag = "$TAG.field"
            hint = "Reply"
            setHintTextColor(Color.argb(255, 170, 170, 170))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            maxLines = 3
            minHeight = dp(40)
            alpha = 1f
            background = pill(opaque(0xFF2C2C2E.toInt()))
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setOnEditorActionListener { _, actionId, event ->
                val send = actionId == EditorInfo.IME_ACTION_SEND ||
                    (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
                if (send) {
                    submit(payload, text?.toString().orEmpty(), module)
                    true
                } else false
            }
        }
        val send = TextView(context).apply {
            text = "➤"
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            alpha = 1f
            background = pill(Color.WHITE)
            val size = dp(40)
            layoutParams = LinearLayout.LayoutParams(size, size).apply { leftMargin = dp(8) }
            setOnClickListener { submit(payload, field.text?.toString().orEmpty(), module) }
        }
        return LinearLayout(context).apply {
            tag = TAG
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 0)
            alpha = 1f
            background = null
            addView(field, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(send)
            isClickable = true
            setOnClickListener { }
        }
    }

    private fun submit(payload: IslandReplyPayload, message: String, module: XposedModule) {
        val text = message.trim()
        if (text.isEmpty()) return
        val replyIntent = Intent()
        val results = Bundle().apply { putCharSequence(payload.resultKey, text) }
        RemoteInput.addResultsToIntent(
            arrayOf(RemoteInput.Builder(payload.resultKey).build()),
            replyIntent,
            results,
        )
        val sendContext = overlay.get()?.context
        runCatching { payload.replyAction.send(sendContext, 0, replyIntent) }
            .onFailure { module.log("HyperBridge: island reply send failed: ${it.message}") }
        dismiss()
    }

    private fun pill(color: Int) = GradientDrawable().apply {
        setColor(opaque(color))
        alpha = 255
        cornerRadius = 48f
    }

    private fun opaque(color: Int): Int = Color.argb(255, Color.red(color), Color.green(color), Color.blue(color))

    private fun findNamed(view: View, simpleName: String): View? {
        if (view.javaClass.simpleName == simpleName) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findNamed(view.getChildAt(index), simpleName)?.let { return it }
            }
        }
        return null
    }

    private fun windowRoots(): List<View> = runCatching {
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        val field = global.getDeclaredField("mViews").apply { isAccessible = true }
        when (val views = field.get(instance)) {
            is List<*> -> views.filterIsInstance<View>()
            is Array<*> -> views.filterIsInstance<View>()
            else -> emptyList()
        }
    }.getOrDefault(emptyList())

    private fun prepareImeWindow(view: View) {
        val root = view.rootView
        val params = root.layoutParams as? WindowManager.LayoutParams ?: return
        if (imeFlags == null) imeFlags = params.flags to params.softInputMode
        params.flags = IslandWindowImePolicy.composerFlags(params.flags)
        params.softInputMode = IslandWindowImePolicy.composerSoftInputMode(params.softInputMode)
        runCatching {
            view.context.getSystemService(WindowManager::class.java)?.updateViewLayout(root, params)
        }
    }

    private fun restoreImeWindow(view: View) {
        val state = imeFlags ?: return
        imeFlags = null
        val root = view.rootView
        val params = root.layoutParams as? WindowManager.LayoutParams ?: return
        params.flags = IslandWindowImePolicy.idleFlags(state.first)
        params.softInputMode = state.second
        runCatching {
            view.context.getSystemService(WindowManager::class.java)?.updateViewLayout(root, params)
        }
    }

    private fun hideIme(view: View) {
        view.context.getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    @SuppressLint("SoonBlockedPrivateApi")
    private fun pendingIntentIntent(pendingIntent: PendingIntent): Intent? = runCatching {
        PendingIntent::class.java.getDeclaredMethod("getIntent").apply { isAccessible = true }
            .invoke(pendingIntent) as? Intent
    }.getOrNull()

    private fun parcelablePendingIntent(intent: Intent, key: String): PendingIntent? {
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(key, PendingIntent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(key)
        }
    }
}
