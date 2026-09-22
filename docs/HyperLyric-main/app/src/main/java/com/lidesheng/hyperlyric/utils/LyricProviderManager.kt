package com.lidesheng.hyperlyric.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.graphics.drawable.toBitmap
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.ui.component.icon.GeminiColor
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

@Immutable
data class ModuleTag(
    val iconRes: Int? = null,
    val imageVector: ImageVector? = null,
    val title: String? = null,
    val titleRes: Int = -1,
    val isRainbow: Boolean = false
)

@Immutable
data class LyricModule(
    val packageName: String,
    val versionName: String?,
    val lastUpdateTime: Long,
    val label: String,
    val description: String?,
    val author: String?,
    val category: String?,
    val tags: List<ModuleTag> = emptyList()
)

@Immutable
data class ProviderUiState(
    val modules: List<LyricModule> = emptyList(),
    val hasLoaded: Boolean = false,
    val isInitialLoading: Boolean = false,
    val isRefreshing: Boolean = false
)

@Immutable
data class ModuleCategory(
    val name: String,
    val items: List<LyricModule>
)

object LyricProviderManager {

    private const val ICON_CACHE_SIZE_KB = 8 * 1024
    private val scanMutex = Mutex()
    private val mutableUiState = MutableStateFlow(ProviderUiState())
    val uiState: StateFlow<ProviderUiState> = mutableUiState.asStateFlow()

    private val iconCache = object : LruCache<String, ImageBitmap>(ICON_CACHE_SIZE_KB) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            return (value.width * value.height * 4 / 1024).coerceAtLeast(1)
        }
    }

    fun getCachedIcon(module: LyricModule, targetSizePx: Int): ImageBitmap? {
        return iconCache.get(iconCacheKey(module, targetSizePx))
    }

    suspend fun loadIcon(
        context: Context,
        module: LyricModule,
        targetSizePx: Int
    ): ImageBitmap? {
        getCachedIcon(module, targetSizePx)?.let { return it }
        return withContext(Dispatchers.IO) {
            getCachedIcon(module, targetSizePx)?.let { return@withContext it }
            try {
                val drawable = context.packageManager.getApplicationIcon(module.packageName)
                val bitmap = drawable.toBitmap(
                    width = targetSizePx,
                    height = targetSizePx,
                    config = Bitmap.Config.ARGB_8888
                ).apply {
                    prepareToDraw()
                }
                bitmap.asImageBitmap().also {
                    iconCache.put(iconCacheKey(module, targetSizePx), it)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun loadProviders(context: Context, forceRefresh: Boolean = false) {
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
                val modules = scanProviders(context)
                mutableUiState.update { state ->
                    if (state.modules == modules && state.hasLoaded) {
                        state.copy(isInitialLoading = false, isRefreshing = false)
                    } else {
                        state.copy(
                            modules = modules,
                            hasLoaded = true,
                            isInitialLoading = false,
                            isRefreshing = false
                        )
                    }
                }
            } catch (e: CancellationException) {
                mutableUiState.update {
                    it.copy(isInitialLoading = false, isRefreshing = false)
                }
                throw e
            } catch (e: Exception) {
                LogManager.e("LyricProviderManager", "加载歌词模块失败", e)
                mutableUiState.update {
                    it.copy(isInitialLoading = false, isRefreshing = false)
                }
            }
        }
    }

    private suspend fun scanProviders(context: Context): List<LyricModule> {
        return withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            @Suppress("DEPRECATION")
            val packageInfos =
                packageManager.getInstalledPackages(PackageManager.GET_META_DATA)

            val loadedModules = packageInfos.asSequence()
                .filter(::isValidModule)
                .mapNotNull { processPackage(packageManager, it) }
                .toList()

            val collator = Collator.getInstance(Locale.getDefault())
            loadedModules.sortedWith { first, second ->
                collator.compare(first.label, second.label)
            }
        }
    }

    private fun isValidModule(packageInfo: PackageInfo): Boolean {
        val appInfo = packageInfo.applicationInfo ?: return false
        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        // 严格遵循 lyricon 逻辑：非系统应用且非更新后的系统应用 (即：!(isSystem || isUpdatedSystem))
        return !(isSystem || isUpdatedSystem) && appInfo.metaData?.getBoolean("lyricon_module") == true
    }

    private fun processPackage(pm: PackageManager, packageInfo: PackageInfo): LyricModule? {
        return try {
            val appInfo = packageInfo.applicationInfo ?: return null
            val metaData = appInfo.metaData ?: return null
            val label = appInfo.loadLabel(pm).toString()

            LyricModule(
                packageName = packageInfo.packageName,
                versionName = packageInfo.versionName,
                lastUpdateTime = packageInfo.lastUpdateTime,
                label = label,
                description = metaData.getString("lyricon_module_description"),
                author = metaData.getString("lyricon_module_author"),
                category = metaData.getString("lyricon_module_category"),
                tags = extractTags(pm, appInfo, metaData)
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extractTags(pm: PackageManager, appInfo: ApplicationInfo, metaData: android.os.Bundle): List<ModuleTag> {
        val tagsResId = metaData.getInt("lyricon_module_tags")
        val rawTags = if (tagsResId != 0) {
            runCatching {
                val resources = pm.getResourcesForApplication(appInfo)
                resources.getStringArray(tagsResId).toList()
            }.getOrDefault(emptyList())
        } else {
            metaData.getString("lyricon_module_tags")?.let { listOf(it) } ?: emptyList()
        }
        return rawTags.mapNotNull { tagKey ->
            if (tagKey.isBlank()) return@mapNotNull null
            getPredefinedTag(tagKey) ?: ModuleTag(title = tagKey)
        }
    }

    private fun getPredefinedTag(key: String): ModuleTag? {
        return when (key) {
            $$"$syllable" -> ModuleTag(
                imageVector = GeminiColor,
                titleRes = R.string.module_tag_syllable,
                isRainbow = true
            )
            $$"$translation" -> ModuleTag(
                iconRes = R.drawable.translate_24px,
                titleRes = R.string.module_tag_translation
            )
            $$"$bluetooth" -> ModuleTag(titleRes = R.string.module_tag_bluetooth)
            else -> null
        }
    }

    fun categorizeModules(modules: List<LyricModule>, defaultCategory: String): List<ModuleCategory> {
        if (modules.isEmpty()) return emptyList()
        val grouped = modules.groupBy { it.category ?: defaultCategory }
        
        if (grouped.size == 1 && grouped.containsKey(defaultCategory)) {
            return listOf(ModuleCategory("", grouped[defaultCategory]!!))
        }
        
        return grouped.map { (name, items) -> ModuleCategory(name, items) }
            .sortedBy { it.name }
    }

    private fun iconCacheKey(module: LyricModule, targetSizePx: Int): String {
        return "${module.packageName}:${module.lastUpdateTime}:$targetSizePx"
    }
}
