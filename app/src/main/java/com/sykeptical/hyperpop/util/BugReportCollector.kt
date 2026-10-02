package com.sykeptical.hyperpop.util

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import com.sykeptical.hyperpop.data.AppPreferences
import com.sykeptical.hyperpop.data.db.AppDatabase
import com.sykeptical.hyperpop.service.diagnostics.DiagnosticsState
import com.sykeptical.hyperpop.service.diagnostics.DiagnosticsStore
import com.sykeptical.hyperpop.xposed.runtime.EnvironmentRuntime
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DeviceDiagnosticInfo(
    val marketingName: String,
    val model: String,
    val manufacturer: String,
    val device: String,
    val androidVersion: String,
    val sdkInt: Int,
    val hyperOSVersion: String,
    val isCNRom: Boolean,
    val isCompatibleOS: Boolean,
    val appVersionName: String,
    val appVersionCode: Int
)

data class PermissionDiagnosticInfo(
    val focusBackendReady: Boolean,
    val rootAvailable: Boolean,
    val lsposedAvailable: Boolean,
    val systemUiHookAlive: Boolean,
    val notificationIngressReady: Boolean,
    val islandDispatcherReady: Boolean,
    val protocolCompatible: Boolean,
)

enum class AppConfigScope {
    NONE,
    SPECIFIC_APP,
    ALL_SETTINGS
}

object BugReportCollector {

    const val DEVELOPER_NAME = "sykeptical"
    const val PRIVACY_POLICY_URL = "https://hyper-bridge.app/privacy/"

    fun collectDeviceInfo(context: Context): DeviceDiagnosticInfo {
        val pInfo = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: Exception) {
            null
        }

        return DeviceDiagnosticInfo(
            marketingName = DeviceUtils.getDeviceMarketName(),
            model = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            device = Build.DEVICE,
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            hyperOSVersion = DeviceUtils.getHyperOSVersion(),
            isCNRom = DeviceUtils.isCNRom,
            isCompatibleOS = DeviceUtils.isCompatibleOS(),
            appVersionName = pInfo?.versionName ?: "0.6.0",
            appVersionCode = pInfo?.longVersionCode?.toInt() ?: 35
        )
    }

    fun collectPermissions(context: Context): PermissionDiagnosticInfo {
        val environment = EnvironmentRuntime.snapshot(context)

        return PermissionDiagnosticInfo(
            focusBackendReady = environment.focusCompatible,
            rootAvailable = environment.rootAvailable,
            lsposedAvailable = environment.libxposedServiceAvailable,
            systemUiHookAlive = environment.systemUiHookAlive,
            notificationIngressReady = environment.notificationIngressReady,
            islandDispatcherReady = environment.islandDispatcherReady,
            protocolCompatible = environment.backendProtocolCompatible,
        )
    }

    suspend fun collectAppConfig(
        context: Context,
        scope: AppConfigScope,
        targetPackage: String? = null
    ): String {
        if (scope == AppConfigScope.NONE) return ""
        val db = AppDatabase.getDatabase(context)
        val allSettings = db.settingsDao().getAllSync()

        return when (scope) {
            AppConfigScope.NONE -> ""
            AppConfigScope.SPECIFIC_APP -> {
                if (targetPackage.isNullOrEmpty()) return ""
                val appSettings = allSettings.filter { setting ->
                    setting.key.contains(targetPackage)
                }
                if (appSettings.isEmpty()) {
                    "No custom overrides saved for $targetPackage (using global defaults)"
                } else {
                    appSettings.joinToString("\n") { "  ${it.key}: ${it.value}" }
                }
            }
            AppConfigScope.ALL_SETTINGS -> {
                allSettings.joinToString("\n") { "  ${it.key}: ${it.value}" }
            }
        }
    }

    fun collectLogcat(maxLines: Int = 500): String {
        return try {
            val process = ProcessBuilder("logcat", "-d", "-v", "time", "-t", maxLines.toString())
                .redirectErrorStream(true)
                .start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val lines = mutableListOf<String>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let { lines.add(it) }
            }
            process.waitFor()
            if (lines.isEmpty()) {
                "No logcat entries captured."
            } else {
                lines.takeLast(maxLines).joinToString("\n")
            }
        } catch (e: Exception) {
            "Failed to capture logs: ${e.message}"
        }
    }

    fun collectDiagnosticsInfo(): DiagnosticsState {
        return DiagnosticsStore.state.value
    }

    fun buildMarkdownReport(
        userDescription: String,
        userSteps: String,
        deviceInfo: DeviceDiagnosticInfo?,
        permissions: PermissionDiagnosticInfo?,
        appConfigScope: AppConfigScope,
        targetPackage: String?,
        appConfigText: String?,
        logcatText: String?,
        diagnosticsState: DiagnosticsState? = null
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val timestamp = dateFormat.format(Date())

        val sb = StringBuilder()
        sb.append("### HyperPop Bug Report & Diagnostics\n")
        if (deviceInfo != null) {
            sb.append("**App Version:** ${deviceInfo.appVersionName} (${deviceInfo.appVersionCode})\n")
        }
        sb.append("**Generated:** $timestamp\n\n")

        // User notes
        sb.append("#### User Report\n")
        sb.append("- **Description:** ${if (userDescription.isNotBlank()) userDescription.trim() else "None provided"}\n")
        sb.append("- **Steps to Reproduce:** ${if (userSteps.isNotBlank()) userSteps.trim() else "None provided"}\n\n")

        // Device
        if (deviceInfo != null) {
            sb.append("#### Device & System Information\n")
            sb.append("- **Device Model:** ${deviceInfo.marketingName} (${deviceInfo.model})\n")
            sb.append("- **Manufacturer / Codename:** ${deviceInfo.manufacturer} / ${deviceInfo.device}\n")
            sb.append("- **HyperOS / MIUI Version:** ${deviceInfo.hyperOSVersion}\n")
            sb.append("- **Android Version:** Android ${deviceInfo.androidVersion} (API ${deviceInfo.sdkInt})\n")
            sb.append("- **ROM Region:** ${if (deviceInfo.isCNRom) "China (CN ROM)" else "Global / Non-CN"}\n")
            sb.append("- **System Compatibility:** ${if (deviceInfo.isCompatibleOS) "Compatible (HyperOS 3+)" else "Untested / Below HyperOS 3"}\n\n")
        }

        // Permissions
        if (permissions != null) {
            sb.append("#### Permissions & System Health\n")
            sb.append("- **Xiaomi Focus hook backend:** ${if (permissions.focusBackendReady) "Ready" else "Unavailable"}\n")
            sb.append("- **Root:** ${if (permissions.rootAvailable) "Available" else "Unavailable"}\n")
            sb.append("- **LSPosed Service:** ${if (permissions.lsposedAvailable) "Available" else "Unavailable"}\n")
            sb.append("- **SystemUI Hook:** ${if (permissions.systemUiHookAlive) "Active" else "Inactive"}\n\n")
            sb.append("- **Notification ingress:** ${if (permissions.notificationIngressReady) "Ready" else "Unavailable"}\n")
            sb.append("- **Island dispatcher:** ${if (permissions.islandDispatcherReady) "Ready" else "Unavailable"}\n")
            sb.append("- **Protocol:** ${if (permissions.protocolCompatible) "Compatible" else "Incompatible"}\n\n")
        }

        // App config
        if (appConfigScope != AppConfigScope.NONE && !appConfigText.isNullOrEmpty()) {
            val scopeLabel = when (appConfigScope) {
                AppConfigScope.ALL_SETTINGS -> "All Apps"
                AppConfigScope.SPECIFIC_APP -> "Specific App${if (targetPackage != null) ": $targetPackage" else ""}"
                AppConfigScope.NONE -> "None"
            }
            sb.append("#### App Configuration ($scopeLabel)\n")
            sb.append("```yaml\n$appConfigText\n```\n\n")
        }

        // Diagnostics & Sanitized Events
        if (diagnosticsState != null) {
            sb.append("#### Diagnostics & Sanitized Events\n")
            sb.append("- **Active Islands:** ${diagnosticsState.activeIslands}\n")
            if (diagnosticsState.lastClassification != null) {
                sb.append("- **Last Classification:** ${diagnosticsState.lastClassification}\n")
            }
            if (diagnosticsState.lastCallState != null) {
                sb.append("- **Last Call State:** ${diagnosticsState.lastCallState}\n")
            }
            if (diagnosticsState.events.isNotEmpty()) {
                sb.append("- **Recent Events (${diagnosticsState.events.size}):**\n")
                diagnosticsState.events.takeLast(20).forEach { ev ->
                    val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ev.timestamp))
                    val details = listOfNotNull(time, ev.classification, ev.action, ev.packageName, ev.reason).joinToString(" · ")
                    sb.append("  - $details\n")
                }
            }
            sb.append("\n")
        }

        // Logs
        if (!logcatText.isNullOrBlank()) {
            sb.append("#### Application Logs (Logcat)\n")
            sb.append("```text\n$logcatText\n```\n")
        }

        return sb.toString()
    }

    fun buildGitHubIssueUrl(
        deviceInfo: DeviceDiagnosticInfo? = null,
        userDescription: String = "",
        userSteps: String = "",
        permissionsInfo: PermissionDiagnosticInfo? = null,
        appConfigScope: AppConfigScope = AppConfigScope.NONE,
        selectedAppPackage: String? = null,
        appConfigText: String? = null,
        logcatText: String? = null,
        diagnosticsState: DiagnosticsState? = null
    ): String {
        val androidOption = when {
            deviceInfo == null -> "Other"
            deviceInfo.sdkInt >= 37 || deviceInfo.androidVersion.startsWith("17") -> "Android 17"
            deviceInfo.sdkInt == 36 || deviceInfo.androidVersion.startsWith("16") -> "Android 16"
            deviceInfo.sdkInt == 35 || deviceInfo.androidVersion.startsWith("15") -> "Android 15"
            else -> "Other"
        }

        val deviceStr = if (deviceInfo != null) {
            "${deviceInfo.marketingName} (${deviceInfo.model})"
        } else ""

        val osVersionStr = deviceInfo?.hyperOSVersion ?: ""

        val params = mutableListOf<Pair<String, String>>()

        val shortTitle = if (userDescription.isNotBlank()) {
            userDescription.trim().take(60)
        } else {
            "Bug report"
        }
        params.add("title" to "[BUG] $shortTitle")

        if (deviceStr.isNotBlank()) {
            params.add("device" to deviceStr)
        }
        if (osVersionStr.isNotBlank()) {
            params.add("os_version" to osVersionStr)
        }
        params.add("android_version" to androidOption)
        params.add("app_version" to "v0.6.x")

        // Build description field containing user description and diagnostics summary
        val descBuilder = StringBuilder()
        if (userDescription.isNotBlank()) {
            descBuilder.append(userDescription.trim())
        }

        val diagSummary = StringBuilder()
        if (permissionsInfo != null) {
            diagSummary.append("\n\n**Permissions & System Health:**\n")
            diagSummary.append("- Focus hook backend: ${if (permissionsInfo.focusBackendReady) "Ready" else "Unavailable"}\n")
            diagSummary.append("- Root: ${if (permissionsInfo.rootAvailable) "Available" else "Unavailable"}\n")
            diagSummary.append("- LSPosed: ${if (permissionsInfo.lsposedAvailable) "Available" else "Unavailable"}\n")
            diagSummary.append("- SystemUI Hook: ${if (permissionsInfo.systemUiHookAlive) "Active" else "Inactive"}\n")
            diagSummary.append("- Notification ingress: ${if (permissionsInfo.notificationIngressReady) "Ready" else "Unavailable"}\n")
            diagSummary.append("- Island dispatcher: ${if (permissionsInfo.islandDispatcherReady) "Ready" else "Unavailable"}\n")
            diagSummary.append("- Protocol: ${if (permissionsInfo.protocolCompatible) "Compatible" else "Incompatible"}\n")
        }

        if (appConfigScope != AppConfigScope.NONE && !appConfigText.isNullOrBlank()) {
            val scopeLabel = if (appConfigScope == AppConfigScope.SPECIFIC_APP) "Specific App: $selectedAppPackage" else "All Apps"
            diagSummary.append("\n**App Config ($scopeLabel):**\n```yaml\n${appConfigText.take(400)}\n```\n")
        }

        if (diagnosticsState != null) {
            diagSummary.append("\n**Diagnostics & Island State:**\n")
            diagSummary.append("- Active Islands: ${diagnosticsState.activeIslands}\n")
            if (diagnosticsState.lastClassification != null) {
                diagSummary.append("- Last Classification: ${diagnosticsState.lastClassification}\n")
            }
            if (diagnosticsState.events.isNotEmpty()) {
                val recentEventsStr = diagnosticsState.events.takeLast(5).joinToString("\n") { ev ->
                    val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ev.timestamp))
                    "  - " + listOfNotNull(time, ev.classification, ev.action, ev.packageName, ev.reason).joinToString(" · ")
                }
                diagSummary.append("- Recent Events:\n$recentEventsStr\n")
            }
        }

        val finalDesc = (descBuilder.toString() + diagSummary.toString()).trim()
        if (finalDesc.isNotBlank()) {
            params.add("description" to finalDesc.take(1500))
        }

        // Steps to reproduce
        val stepsStr = if (userSteps.isNotBlank()) {
            userSteps.trim()
        } else {
            "1. Open HyperPop\n2. \n3. "
        }
        params.add("steps" to stepsStr)

        // Logs excerpt
        if (!logcatText.isNullOrBlank()) {
            params.add("logs" to logcatText.takeLast(1200))
        }

        val queryString = params.joinToString("&") { (k, v) ->
            java.net.URLEncoder.encode(k, "UTF-8").replace("+", "%20") + "=" +
                java.net.URLEncoder.encode(v, "UTF-8").replace("+", "%20")
        }

        return queryString
    }
}
