package com.lidesheng.hyperlyric.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.LruCache
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.graphics.drawable.toBitmap
import com.lidesheng.hyperlyric.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale
import java.util.zip.ZipFile

@Immutable
data class SuperLyricApp(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: String,
    val lastUpdateTime: Long,
    @StringRes val usageResId: Int,
    val apiVersionName: String? = null,
    val apiVersionCode: String? = null
)

@Immutable
data class SuperLyricAppUiState(
    val apiApps: List<SuperLyricApp> = emptyList(),
    val hookApps: List<SuperLyricApp> = emptyList(),
    val hasLoaded: Boolean = false,
    val isInitialLoading: Boolean = false,
    val isRefreshing: Boolean = false
)

object SuperLyricAppManager {

    private data class AppDefinition(
        val packageName: String,
        @StringRes val usageResId: Int
    )

    private data class ApiVersion(
        val name: String,
        val code: String
    )

    private data class ScanResult(
        val apiApps: List<SuperLyricApp>,
        val hookApps: List<SuperLyricApp>
    )

    private const val ICON_CACHE_SIZE_KB = 8 * 1024
    private val scanMutex = Mutex()
    private val mutableUiState = MutableStateFlow(SuperLyricAppUiState())
    val uiState: StateFlow<SuperLyricAppUiState> = mutableUiState.asStateFlow()
    private val collator = Collator.getInstance(Locale.getDefault())
    private val iconCache = object : LruCache<String, ImageBitmap>(ICON_CACHE_SIZE_KB) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            return (value.width * value.height * 4 / 1024).coerceAtLeast(1)
        }
    }

    private val appDefinitions = listOf(
        AppDefinition("remix.myplayer", R.string.superlyric_usage_aplayer),
        AppDefinition("com.apple.android.music", R.string.superlyric_usage_apple_music),
        AppDefinition("cn.aqzscn.stream_music", R.string.superlyric_usage_yinliu),
        AppDefinition("cn.wenyu.bodian", R.string.superlyric_usage_bodian),
        AppDefinition("org.akanework.gramophone", R.string.superlyric_usage_gramophone),
        AppDefinition("com.heytap.music", R.string.superlyric_usage_oppo),
        AppDefinition("com.hiby.music", R.string.superlyric_usage_hiby),
        AppDefinition("com.hihonor.cloudmusic", R.string.superlyric_usage_honor),
        AppDefinition("com.huawei.music", R.string.superlyric_usage_huawei),
        AppDefinition("org.kde.kdeconnect_tp", R.string.superlyric_usage_kde),
        AppDefinition("com.kugou.android", R.string.superlyric_usage_kugou),
        AppDefinition("com.kugou.android.lite", R.string.superlyric_usage_kugou_lite),
        AppDefinition("cn.kuwo.player", R.string.superlyric_usage_kuwo),
        AppDefinition("com.lalilu.lmusic", R.string.superlyric_usage_lmusic),
        AppDefinition("cn.toside.music.mobile", R.string.superlyric_usage_lx_music),
        AppDefinition("com.meizu.media.music", R.string.superlyric_usage_meizu),
        AppDefinition("com.mimicry.mymusic", R.string.superlyric_usage_mimicry),
        AppDefinition("com.miui.player", R.string.superlyric_usage_xiaomi),
        AppDefinition("cmccwm.mobilemusic", R.string.superlyric_usage_migu),
        AppDefinition("fun.upup.musicfree", R.string.superlyric_usage_musicfree),
        AppDefinition("com.netease.cloudmusic", R.string.superlyric_usage_netease),
        AppDefinition("com.oppo.music", R.string.superlyric_usage_oppo),
        AppDefinition("com.maxmpz.audioplayer", R.string.superlyric_usage_poweramp),
        AppDefinition("com.xuncorp.qinalt.music", R.string.superlyric_usage_qingyan),
        AppDefinition("com.luna.music", R.string.superlyric_usage_qishui),
        AppDefinition("com.tencent.qqmusic", R.string.superlyric_usage_qq),
        AppDefinition("com.r.rplayer", R.string.superlyric_usage_rplayer),
        AppDefinition("com.salt.music", R.string.superlyric_usage_salt),
        AppDefinition("com.xuncorp.suvine.music", R.string.superlyric_usage_suvine),
        AppDefinition("app.symfonik.music.player", R.string.superlyric_usage_symfonium),
        AppDefinition("com.spotify.music", R.string.superlyric_usage_spotify)
    ).associateBy(AppDefinition::packageName)

    fun getCachedIcon(app: SuperLyricApp, targetSizePx: Int): ImageBitmap? {
        return iconCache.get(iconCacheKey(app, targetSizePx))
    }

    suspend fun loadIcon(
        context: Context,
        app: SuperLyricApp,
        targetSizePx: Int
    ): ImageBitmap? {
        getCachedIcon(app, targetSizePx)?.let { return it }
        return withContext(Dispatchers.IO) {
            getCachedIcon(app, targetSizePx)?.let { return@withContext it }
            try {
                val bitmap = context.packageManager.getApplicationIcon(app.packageName)
                    .toBitmap(
                        width = targetSizePx,
                        height = targetSizePx,
                        config = Bitmap.Config.ARGB_8888
                    )
                    .apply { prepareToDraw() }
                bitmap.asImageBitmap().also {
                    iconCache.put(iconCacheKey(app, targetSizePx), it)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun loadApps(context: Context, forceRefresh: Boolean = false) {
        scanMutex.withLock {
            val hasCachedData = mutableUiState.value.hasLoaded
            mutableUiState.update { state ->
                when {
                    forceRefresh -> state.copy(isRefreshing = true)
                    !hasCachedData -> state.copy(isInitialLoading = true)
                    else -> state
                }
            }

            try {
                val apps = scanApps(context)
                mutableUiState.update { state ->
                    state.copy(
                        apiApps = apps.apiApps,
                        hookApps = apps.hookApps,
                        hasLoaded = true,
                        isInitialLoading = false,
                        isRefreshing = false
                    )
                }
            } catch (e: CancellationException) {
                mutableUiState.update {
                    it.copy(isInitialLoading = false, isRefreshing = false)
                }
                throw e
            } catch (_: Exception) {
                mutableUiState.update {
                    it.copy(isInitialLoading = false, isRefreshing = false)
                }
            }
        }
    }

    private suspend fun scanApps(context: Context): ScanResult {
        return withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            @Suppress("DEPRECATION")
            val packageInfos = packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
            val apiApps = mutableListOf<SuperLyricApp>()
            val hookApps = mutableListOf<SuperLyricApp>()

            packageInfos.forEach { packageInfo ->
                appDefinitions[packageInfo.packageName]?.let { definition ->
                    processPackage(packageManager, packageInfo, definition.usageResId)?.let(hookApps::add)
                }

                getApiVersion(packageInfo)?.let { apiVersion ->
                    processPackage(
                        packageManager,
                        packageInfo,
                        R.string.superlyric_usage_api,
                        apiVersion
                    )?.let(apiApps::add)
                }
            }

            ScanResult(
                apiApps = apiApps.sortedWith { first, second ->
                    collator.compare(first.label, second.label)
                },
                hookApps = hookApps.sortedWith { first, second ->
                    collator.compare(first.label, second.label)
                }
            )
        }
    }

    private fun processPackage(
        packageManager: PackageManager,
        packageInfo: PackageInfo,
        @StringRes usageResId: Int,
        apiVersion: ApiVersion? = null
    ): SuperLyricApp? {
        return try {
            val appInfo = packageInfo.applicationInfo ?: return null
            SuperLyricApp(
                packageName = packageInfo.packageName,
                label = appInfo.loadLabel(packageManager).toString(),
                versionName = packageInfo.versionName,
                versionCode = PackageInfoCompat.getLongVersionCode(packageInfo).toString(),
                apiVersionName = apiVersion?.name,
                apiVersionCode = apiVersion?.code,
                lastUpdateTime = packageInfo.lastUpdateTime,
                usageResId = usageResId
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun getApiVersion(packageInfo: PackageInfo): ApiVersion? {
        val appInfo = packageInfo.applicationInfo ?: return null
        val metaData = appInfo.metaData ?: return null
        if (!metaData.getBoolean("superlyricapi") ||
            metaData.getBoolean("xposedmodule") ||
            hasXposedModule(appInfo.sourceDir)
        ) {
            return null
        }
        return ApiVersion(
            name = metaData.getFloat("superlyricapi_version_name").toString(),
            code = metaData.getInt("superlyricapi_version_code").toString()
        )
    }

    private fun hasXposedModule(apkPath: String): Boolean {
        return try {
            ZipFile(apkPath).use { zipFile ->
                zipFile.entries().asSequence().any { entry ->
                    entry.name.startsWith("META-INF/xposed")
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun iconCacheKey(app: SuperLyricApp, targetSizePx: Int): String {
        return "${app.packageName}:${app.lastUpdateTime}:$targetSizePx"
    }
}
