package com.sykeptical.hyperpop

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.UserManager
import android.util.Log
import com.sykeptical.hyperpop.data.AppPreferences
import com.sykeptical.hyperpop.island.backend.HookConfigSync
import com.sykeptical.hyperpop.island.backend.IslandProtocol
import com.sykeptical.hyperpop.root.RootShellService
import com.sykeptical.hyperpop.xposed.runtime.ModuleServiceSnapshot
import com.sykeptical.hyperpop.xposed.runtime.ModuleServiceState
import com.sykeptical.hyperpop.xposed.runtime.EnvironmentRuntime
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

private data class ScreenRecorderHookConfig(
    val replace: Boolean,
    val immediate: Boolean,
    val countdown: Boolean,
    val icon: String,
)

class HyperPopApplication : Application(), XposedServiceHelper.OnServiceListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var xposedService: XposedService? = null
    private val config: SharedPreferences by lazy {
        getSharedPreferences(IslandProtocol.REMOTE_PREFS, MODE_PRIVATE)
    }
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> syncHookConfig() }
    private val configSyncStarted = AtomicBoolean(false)
    private val policySyncStarted = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(this)

        startCredentialProtectedStateWhenUnlocked()
        scope.launch {
            val rootAvailable = RootShellService.isAvailable()
            EnvironmentRuntime.setRootAvailable(rootAvailable)
        }
    }

    private fun startCredentialProtectedStateWhenUnlocked() {
        if (getSystemService(UserManager::class.java)?.isUserUnlocked == false) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    unregisterReceiver(this)
                    startCredentialProtectedStateWhenUnlocked()
                }
            }
            registerReceiver(receiver, IntentFilter(Intent.ACTION_USER_UNLOCKED), Context.RECEIVER_NOT_EXPORTED)
            return
        }
        if (configSyncStarted.compareAndSet(false, true)) {
            config.registerOnSharedPreferenceChangeListener(listener)
            HookConfigSync.initialize(this)
            syncHookConfig()
        }
        if (!policySyncStarted.compareAndSet(false, true)) return
        val preferences = AppPreferences(this)
        scope.launch {
            combine(preferences.allowedPackagesFlow, preferences.notificationTypePolicyFlow) { packages, policy ->
                Triple(packages, policy.globalTypes, policy.appOverrides)
            }.collect { (packages, globalTypes, overrides) ->
                HookConfigSync.updatePolicy(this@HyperPopApplication, packages, globalTypes, overrides)
            }
        }
        scope.launch {
            preferences.callStagePolicyFlow.collect { policy ->
                HookConfigSync.updateCallStagePolicy(
                    this@HyperPopApplication,
                    policy.globalStages,
                    policy.appOverrides,
                )
            }
        }
        scope.launch {
            combine(
                preferences.screenRecordingReplaceFloatingFlow,
                preferences.screenRecordingImmediateStartFlow,
                preferences.screenRecordingCountdownEnabledFlow,
                preferences.screenRecordingIconStyleFlow,
            ) { replace, immediate, countdown, icon ->
                ScreenRecorderHookConfig(replace, immediate, countdown, icon)
            }.collect { recorderConfig ->
                HookConfigSync.setScreenRecorderReplacement(
                    this@HyperPopApplication,
                    recorderConfig.replace,
                    recorderConfig.immediate,
                    recorderConfig.countdown,
                    recorderConfig.icon,
                )
            }
        }
    }

    override fun onServiceBind(service: XposedService) {
        xposedService = service
        ModuleServiceState.update(
            ModuleServiceSnapshot(
                available = true,
                apiVersion = service.apiVersion,
                frameworkName = service.frameworkName,
                frameworkVersion = service.frameworkVersion,
                scopes = service.scope.toSet(),
            )
        )
        syncHookConfig()
    }

    override fun onServiceDied(service: XposedService) {
        if (xposedService === service) xposedService = null
        ModuleServiceState.clear()
    }

    fun syncHookConfig() {
        if (getSystemService(UserManager::class.java)?.isUserUnlocked == false) return
        val service = xposedService ?: return
        runCatching {
            val remote = service.getRemotePreferences(IslandProtocol.REMOTE_PREFS)
            val editor = remote.edit() ?: error("RemotePreferences editor unavailable")
            val localValues = config.all
            (remote.all.keys - localValues.keys).forEach(editor::remove)
            localValues.forEach { (key, value) ->
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is String -> editor.putString(key, value)
                    is Set<*> -> @Suppress("UNCHECKED_CAST") editor.putStringSet(key, value as Set<String>)
                }
            }
            check(editor.commit()) { "RemotePreferences commit failed" }
        }.onFailure { Log.w(TAG, "Hook config sync failed", it) }
    }

    fun requestRequiredScopes(onResult: (Result<Set<String>>) -> Unit) {
        requestScopes(setOf(IslandProtocol.SYSTEM_UI_PACKAGE, IslandProtocol.XMSF_PACKAGE), onResult)
    }

    fun requestScopes(packages: Collection<String>, onResult: (Result<Set<String>>) -> Unit) {
        val service = xposedService ?: return onResult(Result.failure(IllegalStateException("LSPosed service unavailable")))
        val missing = packages.toSet() - service.scope.toSet()
        if (missing.isEmpty()) return onResult(Result.success(service.scope.toSet()))
        service.requestScope(missing.toList(), object : XposedService.OnScopeEventListener {
            override fun onScopeRequestApproved(scope: List<String>) {
                ModuleServiceState.update(ModuleServiceState.state.value.copy(scopes = scope.toSet()))
                onResult(Result.success(scope.toSet()))
            }
            override fun onScopeRequestFailed(message: String) {
                onResult(Result.failure(IllegalStateException(message)))
            }
        })
    }

    companion object { private const val TAG = "HyperPopApplication" }
}
