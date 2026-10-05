package com.sykeptical.hyperpop.xposed.hooks

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.view.View
import com.sykeptical.hyperpop.xposed.FocusShadeBackgroundPolicy
import com.sykeptical.hyperpop.xposed.HookConfig
import com.sykeptical.hyperpop.xposed.OrdinaryBlendResources
import com.sykeptical.hyperpop.xposed.log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * Paints the shade media card with the same ordinary notification background
 * the Focus row hook selects. Xiaomi's media card has its own
 * `updateMediaBackground` path and does not read `mIsFocusNotification`.
 */
object MediaShadeBackgroundHook {
    private const val CONTROLLER =
        "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewControllerImpl"
    private const val BLUR_UTIL = "com.miui.systemui.notification.MiuiBaseNotifUtil"
    private const val BLUR_COMPAT = "com.miui.systemui.util.MiBlurCompat"
    private const val NOTIFICATION_UTIL =
        "com.android.systemui.statusbar.notification.utils.NotificationUtil"
    private const val FULL_AOD =
        "com.android.systemui.statusbar.notification.fullaod.NotifiFullAodController"
    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"

    @Volatile private var active = false
    @Volatile private var logged = false
    @Volatile private var loggedFailure = false

    fun isActive(): Boolean = active

    fun install(module: XposedModule, param: PackageLoadedParam) {
        runCatching {
            val controller = param.defaultClassLoader.loadClass(CONTROLLER)
            val updateBackground = controller.getDeclaredMethod("updateMediaBackground")
            module.hook(updateBackground).intercept { chain ->
                val result = chain.proceed()
                if (FocusShadeBackgroundPolicy.shouldRestyleMediaCard(HookConfig.regularShadeBackground())) {
                    val applied = runCatching { restyle(chain.thisObject) }
                        .getOrElse {
                            if (!loggedFailure) {
                                loggedFailure = true
                                module.log("HyperPop: media shade background restyle failed open: ${it.message}")
                            }
                            false
                        }
                    if (applied && !logged) {
                        logged = true
                        module.log("HyperPop: applied regular shade background to the media card")
                    }
                }
                result
            }
            active = true
            module.log("HyperPop: hooked Xiaomi media card background")
        }.onFailure {
            module.log("HyperPop: media card background hook unavailable; failing open: ${it.message}")
        }
    }

    private fun restyle(controller: Any): Boolean {
        val context = IslandHookReflection.readField(controller, "context") as? Context ?: return false
        val holder = IslandHookReflection.readField(controller, "holder") ?: return false
        val mediaBg = IslandHookReflection.readField(holder, "mediaBg") as? View ?: return false
        val loader = controller.javaClass.classLoader ?: return false
        val fullAod = fullAodEnabled(loader)
        val blurOpened = blurOpened(loader, context)
        val keyguard = (IslandHookReflection.readField(controller, "statusBarState") as? Int) == 1
        val drawableContext = if (!blurOpened && fullAod) nightContext(context) else context
        val drawable = drawable(
            drawableContext,
            FocusShadeBackgroundPolicy.ordinaryDrawableName(blurOpened, fullAod),
        ) ?: return false
        val blend = FocusShadeBackgroundPolicy.ordinaryBlendResources(blurOpened, keyguard)
        if (blend == null) {
            clearBlur(loader, mediaBg)
            mediaBg.background = drawable
            return true
        }
        val colorContext = if (fullAod && keyguard) nightContext(context) else context
        val colors = blendColors(colorContext, context, blend) ?: return false
        mediaBg.background = drawable
        return applyOrdinaryBlend(loader, context, mediaBg, colors)
    }

    private fun blendColors(
        colorContext: Context,
        modeContext: Context,
        blend: OrdinaryBlendResources,
    ): IntArray? {
        val color1 = color(colorContext, blend.color1)
        val color2 = color(colorContext, blend.color2)
        val mode1 = integer(modeContext, blend.mode1)
        val mode2 = integer(modeContext, blend.mode2)
        if (color1 == null || color2 == null || mode1 == null || mode2 == null) return null
        return intArrayOf(color1, mode1, color2, mode2)
    }

    private fun applyOrdinaryBlend(
        loader: ClassLoader,
        context: Context,
        view: View,
        colors: IntArray,
    ): Boolean {
        val util = loader.loadClass(NOTIFICATION_UTIL)
        val method = util.declaredMethods.firstOrNull {
            it.name == "applyElementViewBlend" && it.parameterCount == 8
        } ?: return false
        method.isAccessible = true
        // The media card already owns its outline. Only the blend colors change,
        // matching Xiaomi's own call on this view (round-rect flag false).
        method.invoke(null, context, view, false, colors[0], colors[1], colors[2], colors[3], false)
        return true
    }

    private fun clearBlur(loader: ClassLoader, view: View) {
        val compat = loader.loadClass(BLUR_COMPAT)
        invokeStatic(compat, "setMiViewBlurModeCompat", 0, view)
        invokeStatic(compat, "clearMiBackgroundBlendColorCompat", view)
    }

    private fun blurOpened(loader: ClassLoader, context: Context): Boolean {
        val util = loader.loadClass(BLUR_UTIL)
        val method = util.declaredMethods.firstOrNull {
            it.name == "isBackgroundBlurOpened" && it.parameterCount == 1
        } ?: return false
        method.isAccessible = true
        return method.invoke(null, context) as? Boolean ?: false
    }

    private fun fullAodEnabled(loader: ClassLoader): Boolean {
        val controller = loader.loadClass(FULL_AOD)
        val field = controller.getDeclaredField("mEnableFullAod")
        field.isAccessible = true
        return field.getBoolean(null)
    }

    private fun nightContext(context: Context): Context {
        val current = context.resources.configuration
        val uiMode = current.uiMode
        if ((uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
            return context
        }
        val configuration = Configuration(current)
        configuration.uiMode =
            (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        return context.createConfigurationContext(configuration)
    }

    private fun drawable(context: Context, name: String): Drawable? {
        val id = identifier(context, "drawable", name)
        if (id == 0) return null
        return context.getDrawable(id)
    }

    private fun color(context: Context, name: String): Int? {
        val id = identifier(context, "color", name)
        if (id == 0) return null
        return context.getColor(id)
    }

    private fun integer(context: Context, name: String): Int? {
        val id = identifier(context, "integer", name)
        if (id == 0) return null
        return context.resources.getInteger(id)
    }

    private fun identifier(context: Context, type: String, name: String): Int {
        val own = context.resources.getIdentifier(name, type, context.packageName)
        if (own != 0) return own
        return context.resources.getIdentifier(name, type, SYSTEM_UI_PACKAGE)
    }

    private fun invokeStatic(clazz: Class<*>, name: String, vararg args: Any?) {
        val method = clazz.declaredMethods.firstOrNull { candidate ->
            candidate.name == name && candidate.parameterCount == args.size
        } ?: return
        method.isAccessible = true
        method.invoke(null, *args)
    }
}
