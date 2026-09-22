package com.lidesheng.hyperlyric.root.mediacard.island

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.lidesheng.hyperlyric.root.mediacard.island.layout.ios.IslandExpandedMediaIosMetrics

import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.roundToInt

internal object IslandExpandedMediaMusicWaveController {
    private const val LOTTIE_VIEW_CLASS = "com.airbnb.lottie.LottieAnimationView"
    private const val CONSTRAINT_LAYOUT_PARAMS_CLASS =
        "androidx.constraintlayout.widget.ConstraintLayout\$LayoutParams"
    private const val SYSTEM_UI_PLUGIN_PACKAGE = "miui.systemui.plugin"
    private const val MUSIC_WAVE_CACHE_KEY = "hyperlyric_island_expanded_music_wave"
    private const val MUSIC_WAVE_ALPHA = 0.6f

    private val states = Collections.synchronizedMap(WeakHashMap<ViewGroup, MusicWaveState>())
    private val apis = Collections.synchronizedMap(WeakHashMap<ClassLoader, MusicWaveApi>())

    fun apply(
        player: ViewGroup,
        color: Int,
        playing: Boolean
    ) {
        val state = synchronized(states) {
            states[player] ?: createState(player)?.also { states[player] = it }
        } ?: return
        state.updateLayout(player)
        state.setColor(color)
        state.setPlaying(playing)
    }

    fun remove(player: ViewGroup) {
        synchronized(states) { states.remove(player) }?.release()
    }

    fun cleanup() {
        val snapshot = synchronized(states) {
            states.values.toList().also { states.clear() }
        }
        snapshot.forEach(MusicWaveState::release)
        apis.clear()
    }

    private fun createState(player: ViewGroup): MusicWaveState? {
        val classLoader = player.javaClass.classLoader ?: return null
        val api = synchronized(apis) {
            apis[classLoader] ?: MusicWaveApi.createOrNull(classLoader)?.also {
                apis[classLoader] = it
            }
        } ?: return null
        return api.create(player)
    }

    private data class MusicWaveState(
        val view: ImageView,
        val api: MusicWaveApi,
        var color: Int? = null,
        var playing: Boolean? = null
    ) {
        fun updateLayout(player: ViewGroup) {
            api.updateLayout(view, player)
        }

        fun setColor(nextColor: Int) {
            if (color == nextColor) return
            api.setColor(view, nextColor)
            color = nextColor
        }

        fun setPlaying(shouldPlay: Boolean) {
            if (playing == shouldPlay) return
            if (playing == null && !shouldPlay) {
                playing = false
                return
            }
            api.setPlaying(view, shouldPlay)
            if (!shouldPlay) color?.let { api.setColor(view, it) }
            playing = shouldPlay
        }

        fun release() {
            api.release(view)
        }
    }

    private class MusicWaveApi private constructor(
        private val viewConstructor: Constructor<*>,
        private val layoutParamsConstructor: Constructor<*>,
        private val topToTopField: java.lang.reflect.Field,
        private val endToEndField: java.lang.reflect.Field,
        private val setAnimationFromJsonMethod: Method,
        private val setRepeatCountMethod: Method,
        private val playAnimationMethod: Method,
        private val pauseAnimationMethod: Method,
        private val cancelAnimationMethod: Method,
        private val keyPathConstructor: Constructor<*>,
        private val colorFilterProperty: Any,
        private val colorFilterConstructor: Constructor<*>,
        private val valueCallbackConstructor: Constructor<*>,
        private val addValueCallbackMethod: Method
    ) {
        private var musicWaveJson: String? = null

        fun create(player: ViewGroup): MusicWaveState? = runCatching {
            val view = viewConstructor.newInstance(player.context) as ImageView
            view.id = View.generateViewId()
            view.scaleType = ImageView.ScaleType.FIT_CENTER
            view.alpha = MUSIC_WAVE_ALPHA
            view.isClickable = false
            view.isFocusable = false
            view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            view.visibility = View.VISIBLE
            updateLayout(view, player)
            setAnimationFromJsonMethod.invoke(
                view,
                loadMusicWaveJson(player.context),
                MUSIC_WAVE_CACHE_KEY
            )
            setRepeatCountMethod.invoke(view, -1)
            player.addView(view)
            MusicWaveState(view, this)
        }.getOrNull()

        fun updateLayout(view: ImageView, player: ViewGroup) {
            val density = player.resources.displayMetrics.density
            val size = (
                    IslandExpandedMediaIosMetrics.MUSIC_WAVE_SIZE_DP * density
                    ).roundToInt()
            val params =
                layoutParamsConstructor.newInstance(size, size) as ViewGroup.MarginLayoutParams
            topToTopField.setInt(params, 0)
            endToEndField.setInt(params, 0)
            params.topMargin = (
                    IslandExpandedMediaIosMetrics.MUSIC_WAVE_TOP_DP * density
                    ).roundToInt()
            params.marginEnd = (
                    IslandExpandedMediaIosMetrics.MUSIC_WAVE_END_DP * density
                    ).roundToInt()
            view.layoutParams = params
            view.requestLayout()
        }

        fun setColor(view: ImageView, color: Int) {
            val opaqueColor = (color and 0x00FFFFFF) or -0x1000000
            val keyPath = keyPathConstructor.newInstance(arrayOf("**") as Any)
            val colorFilter = colorFilterConstructor.newInstance(opaqueColor)
            val callback = valueCallbackConstructor.newInstance(colorFilter)
            addValueCallbackMethod.invoke(view, keyPath, colorFilterProperty, callback)
            view.invalidate()
        }

        fun setPlaying(view: ImageView, isPlaying: Boolean) {
            if (isPlaying) playAnimationMethod.invoke(view)
            else pauseAnimationMethod.invoke(view)
        }

        fun release(view: ImageView) {
            runCatching { cancelAnimationMethod.invoke(view) }
            (view.parent as? ViewGroup)?.removeView(view)
        }

        private fun loadMusicWaveJson(context: Context): String {
            musicWaveJson?.let { return it }
            val pluginContext = context.createPackageContext(
                SYSTEM_UI_PLUGIN_PACKAGE,
                Context.CONTEXT_IGNORE_SECURITY
            )
            val rawId = pluginContext.resources.getIdentifier(
                "music_wave",
                "raw",
                SYSTEM_UI_PLUGIN_PACKAGE
            )
            require(rawId != 0) { "Missing SystemUI plugin raw/music_wave" }
            return pluginContext.resources.openRawResource(rawId)
                .bufferedReader()
                .use { it.readText() }
                .also { musicWaveJson = it }
        }

        companion object {
            fun createOrNull(classLoader: ClassLoader): MusicWaveApi? = runCatching {
                val lottieViewClass = classLoader.loadClass(LOTTIE_VIEW_CLASS)
                val layoutParamsClass = classLoader.loadClass(CONSTRAINT_LAYOUT_PARAMS_CLASS)
                val keyPathClass = classLoader.loadClass("com.airbnb.lottie.model.KeyPath")
                val lottiePropertyClass = classLoader.loadClass("com.airbnb.lottie.LottieProperty")
                val colorFilterClass = classLoader.loadClass("com.airbnb.lottie.SimpleColorFilter")
                val valueCallbackClass = classLoader.loadClass(
                    "com.airbnb.lottie.value.LottieValueCallback"
                )
                val integer = requireNotNull(Int::class.javaPrimitiveType)
                MusicWaveApi(
                    viewConstructor = lottieViewClass.getDeclaredConstructor(Context::class.java)
                        .apply { isAccessible = true },
                    layoutParamsConstructor = layoutParamsClass.getDeclaredConstructor(
                        integer,
                        integer
                    ).apply { isAccessible = true },
                    topToTopField = layoutParamsClass.getField("topToTop").apply {
                        isAccessible = true
                    },
                    endToEndField = layoutParamsClass.getField("endToEnd").apply {
                        isAccessible = true
                    },
                    setAnimationFromJsonMethod = lottieViewClass.getDeclaredMethod(
                        "setAnimationFromJson",
                        String::class.java,
                        String::class.java
                    ).apply { isAccessible = true },
                    setRepeatCountMethod = lottieViewClass.getDeclaredMethod(
                        "setRepeatCount",
                        integer
                    ).apply { isAccessible = true },
                    playAnimationMethod = lottieViewClass.getDeclaredMethod("playAnimation").apply {
                        isAccessible = true
                    },
                    pauseAnimationMethod = lottieViewClass.getDeclaredMethod("pauseAnimation")
                        .apply {
                            isAccessible = true
                        },
                    cancelAnimationMethod = lottieViewClass.getDeclaredMethod("cancelAnimation")
                        .apply {
                            isAccessible = true
                        },
                    keyPathConstructor = keyPathClass.getDeclaredConstructor(
                        Array<String>::class.java
                    ).apply { isAccessible = true },
                    colorFilterProperty = requireNotNull(
                        lottiePropertyClass.getDeclaredField("COLOR_FILTER")
                            .apply { isAccessible = true }
                            .get(null)
                    ),
                    colorFilterConstructor = colorFilterClass.getDeclaredConstructor(integer)
                        .apply { isAccessible = true },
                    valueCallbackConstructor = valueCallbackClass.getDeclaredConstructor(
                        colorFilterClass
                    ).apply { isAccessible = true },
                    addValueCallbackMethod = lottieViewClass.getDeclaredMethod(
                        "addValueCallback",
                        keyPathClass,
                        Any::class.java,
                        valueCallbackClass
                    ).apply { isAccessible = true }
                )
            }.getOrNull()
        }
    }
}
