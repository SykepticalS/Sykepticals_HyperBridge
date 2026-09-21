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
import com.d4viddf.hyperbridge.ui.InlineReplyActivity
import com.d4viddf.hyperbridge.ui.InlineReplyIntents
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
    private val collapseCallbacks = setOf(
        "onDynamicPluginCallback_expandedToBig",
        "onDynamicPluginCallback_expandedToSmall",
        "onDynamicPluginCallback_bigToSmall",
    )
    private val loaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>()),
    )
    @Volatile private var sendHooked = false

    fun install(module: XposedModule, param: PackageLoadedParam) {
        hookPendingIntentSend(module, param.defaultClassLoader)
        hook(module, param.defaultClassLoader)
        DynamicClassLoaderHooks.observe(module, param.defaultClassLoader) { loader ->
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
                    val pendingIntent = chain.thisObject as? PendingIntent
                        ?: return@intercept chain.proceed()
                    val payload = IslandReplyComposer.payloadFrom(pendingIntent)
                        ?: return@intercept chain.proceed()
                    if (handling.get() == true) return@intercept null
                    handling.set(true)
                    try {
                        val context = chain.args.firstOrNull { it is Context } as? Context
                        IslandReplyComposer.open(context, payload, module)
                    } finally {
                        handling.set(false)
                    }
                    null
                }
            }
            module.log("HyperBridge: island inline-reply PendingIntent send hooked count=${methods.size}")
        }.onFailure {
            sendHooked = false
            module.log("HyperBridge: island inline-reply send hook unavailable: ${it.message}")
        }
    }

    private fun hook(module: XposedModule, loader: ClassLoader) {
        if (!loaders.add(loader)) return
        runCatching {
            val controller = loader.loadClass(CONTROLLER)
            val callback = findCallback(controller)
            module.hook(callback).intercept { chain ->
                val name = chain.args.getOrNull(0) as? String
                val result = chain.proceed()
                if (name in collapseCallbacks) IslandReplyComposer.dismiss()
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

    fun open(context: Context?, payload: IslandReplyPayload, module: XposedModule) {
        val run = {
            if (!show(context, payload, module)) fallback(context, payload, module)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) run() else main.post(run)
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { dismiss() }
            return
        }
        val view = overlay.get() ?: return
        hideIme(view)
        restoreImeWindow(view)
        (view.parent as? ViewGroup)?.removeView(view)
        overlay = WeakReference(null)
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

    private fun show(context: Context?, payload: IslandReplyPayload, module: XposedModule): Boolean {
        val host = findIslandHost()
        if (host == null) {
            module.log("HyperBridge: island reply host missing, falling back")
            return false
        }
        dismiss()
        val composer = buildComposer(host.context ?: context ?: return false, payload, module)
        attach(host, composer)
        overlay = WeakReference(composer)
        composer.post {
            val field = composer.findViewWithTag<EditText>("$TAG.field") ?: return@post
            prepareImeWindow(field)
            field.requestFocus()
            field.context.getSystemService(InputMethodManager::class.java)
                ?.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
        }
        module.log("HyperBridge: island reply composer attached to ${host.javaClass.simpleName}")
        return true
    }

    private fun fallback(context: Context?, payload: IslandReplyPayload, module: XposedModule) {
        val app = context?.applicationContext ?: return
        runCatching {
            app.startActivity(
                InlineReplyIntents.launchIntent(
                    app,
                    payload.replyAction,
                    payload.resultKey,
                    payload.sourcePackage,
                ),
            )
        }.onFailure {
            module.log("HyperBridge: island reply fallback failed: ${it.message}")
        }
    }

    private fun attach(host: ViewGroup, child: View) {
        var current: View? = host
        repeat(4) {
            val parent = current as? ViewGroup ?: return@repeat
            parent.clipChildren = false
            parent.clipToPadding = false
            current = parent.parent as? View
        }
        child.layoutParams = when (host) {
            is FrameLayout -> FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            )
            is LinearLayout -> LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            else -> ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        host.addView(child)
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
            setHintTextColor(0x99FFFFFF.toInt())
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            maxLines = 3
            minHeight = dp(40)
            background = pill(0x33FFFFFF)
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
            background = pill(0xFFFFFFFF.toInt())
            val size = dp(40)
            layoutParams = LinearLayout.LayoutParams(size, size).apply { leftMargin = dp(8) }
            setOnClickListener { submit(payload, field.text?.toString().orEmpty(), module) }
        }
        return LinearLayout(context).apply {
            tag = TAG
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(10))
            setBackgroundColor(0xCC101010.toInt())
            addView(field, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(send)
            isClickable = true
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
        setColor(color)
        cornerRadius = 48f
    }

    private fun findIslandHost(): ViewGroup? {
        val roots = windowRoots()
        val names = arrayOf(
            "DynamicIslandExpandedView",
            "DynamicIslandWindowView",
            "DynamicIslandBigIslandView",
        )
        for (name in names) {
            for (root in roots) {
                val found = findNamed(root, name) as? ViewGroup ?: continue
                if (found.isShown && found.width > 0 && found.height > 0) return found
            }
        }
        return null
    }

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
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM.inv()
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        runCatching {
            view.context.getSystemService(WindowManager::class.java)?.updateViewLayout(root, params)
        }
    }

    private fun restoreImeWindow(view: View) {
        val state = imeFlags ?: return
        imeFlags = null
        val root = view.rootView
        val params = root.layoutParams as? WindowManager.LayoutParams ?: return
        params.flags = state.first
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
