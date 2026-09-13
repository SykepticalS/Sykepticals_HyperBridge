package com.d4viddf.hyperbridge.service.popup

import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.CompanionDeviceManager
import android.content.ComponentName
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import com.d4viddf.hyperbridge.service.NotificationReaderService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PopupSuppressionCapabilityState {
    NEEDS_NOTIFICATION_ACCESS,
    NEEDS_ASSOCIATION,
    WAITING_FOR_LISTENER,
    VERIFYING,
    READY,
    UNSUPPORTED,
    ERROR
}

data class PopupCapabilitySignals(
    val featureSupported: Boolean,
    val notificationAccess: Boolean,
    val associationExists: Boolean,
    val listenerConnected: Boolean,
    val apiVerification: Boolean?,
    val failed: Boolean = false
)

object PopupCapabilityPolicy {
    fun resolve(signals: PopupCapabilitySignals): PopupSuppressionCapabilityState = when {
        !signals.featureSupported -> PopupSuppressionCapabilityState.UNSUPPORTED
        !signals.notificationAccess -> PopupSuppressionCapabilityState.NEEDS_NOTIFICATION_ACCESS
        !signals.associationExists -> PopupSuppressionCapabilityState.NEEDS_ASSOCIATION
        !signals.listenerConnected -> PopupSuppressionCapabilityState.WAITING_FOR_LISTENER
        signals.failed || signals.apiVerification == false -> PopupSuppressionCapabilityState.ERROR
        signals.apiVerification == null -> PopupSuppressionCapabilityState.VERIFYING
        else -> PopupSuppressionCapabilityState.READY
    }
}

object PopupOnboardingPolicy {
    fun canFinishFreshSetup(
        state: PopupSuppressionCapabilityState,
        enabled: Boolean
    ): Boolean = state == PopupSuppressionCapabilityState.UNSUPPORTED ||
        (state == PopupSuppressionCapabilityState.READY && enabled)

    fun shouldBlockUpgrade(
        setupComplete: Boolean,
        upgradeRequired: Boolean,
        enabled: Boolean,
        intentionallyDisabled: Boolean,
        state: PopupSuppressionCapabilityState
    ): Boolean {
        if (!setupComplete || intentionallyDisabled) return false
        val needsRepair = enabled && state != PopupSuppressionCapabilityState.READY
        return upgradeRequired || needsRepair
    }
}

data class PopupChannelDiagnostic(
    val packageName: String,
    val channelId: String,
    val state: ChannelSemanticState,
    val managed: Boolean
)

object PopupControlRuntime {
    private val _apiVerification = MutableStateFlow<Boolean?>(null)
    val apiVerification = _apiVerification.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError = _lastError.asStateFlow()

    private val _channelDiagnostics = MutableStateFlow<List<PopupChannelDiagnostic>>(emptyList())
    val channelDiagnostics = _channelDiagnostics.asStateFlow()

    fun verifying() {
        _apiVerification.value = null
        _lastError.value = null
    }

    fun verified() {
        _apiVerification.value = true
        _lastError.value = null
    }

    fun failed(error: Throwable) {
        _apiVerification.value = false
        _lastError.value = error.javaClass.simpleName
    }

    fun disconnected() {
        _apiVerification.value = null
    }

    fun updateDiagnostics(value: List<PopupChannelDiagnostic>) {
        _channelDiagnostics.value = value
    }
}

class CompanionAssociationManager(private val context: Context) {
    private val manager: CompanionDeviceManager?
        get() = context.getSystemService(CompanionDeviceManager::class.java)

    fun isSupported(): Boolean = context.packageManager.hasSystemFeature(
        PackageManager.FEATURE_COMPANION_DEVICE_SETUP
    ) && manager != null

    fun associations(): List<AssociationInfo> = if (!isSupported()) {
        emptyList()
    } else {
        runCatching { manager?.myAssociations.orEmpty() }.getOrDefault(emptyList())
    }

    fun hasAssociation(): Boolean = associations().any { !it.isSelfManaged }

    fun requestAssociation(
        onPending: (IntentSender) -> Unit,
        onCreated: (AssociationInfo) -> Unit,
        onFailure: (CharSequence?) -> Unit
    ) {
        val service = manager ?: run {
            onFailure("Companion device setup is unavailable")
            return
        }
        val request = AssociationRequest.Builder().build()
        service.associate(
            request,
            context.mainExecutor,
            object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(intentSender: IntentSender) = onPending(intentSender)
                override fun onAssociationCreated(associationInfo: AssociationInfo) = onCreated(associationInfo)
                override fun onFailure(errorMessage: CharSequence?) = onFailure(errorMessage)
            }
        )
    }

    fun currentState(notificationAccess: Boolean): PopupSuppressionCapabilityState =
        PopupCapabilityPolicy.resolve(
            PopupCapabilitySignals(
                featureSupported = isSupported(),
                notificationAccess = notificationAccess,
                associationExists = hasAssociation(),
                listenerConnected = NotificationReaderService.isConnected,
                apiVerification = PopupControlRuntime.apiVerification.value,
                failed = PopupControlRuntime.lastError.value != null
            )
        )

    fun listenerComponent(): ComponentName = ComponentName(context, NotificationReaderService::class.java)
}
