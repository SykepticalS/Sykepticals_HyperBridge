package com.d4viddf.hyperbridge.service.popup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.UserHandle
import android.util.Log
import com.d4viddf.hyperbridge.data.AppPreferences
import com.d4viddf.hyperbridge.data.db.PopupChannelSnapshotEntity
import com.d4viddf.hyperbridge.data.db.PopupControlDao
import com.d4viddf.hyperbridge.service.EffectiveNotificationTypeResolver
import com.d4viddf.hyperbridge.service.SemanticSignature
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

interface PopupChannelAccess {
    val isConnected: Boolean
    fun getChannels(packageName: String, user: UserHandle): List<NotificationChannel>
    fun updateChannel(packageName: String, user: UserHandle, channel: NotificationChannel)
}

enum class PopupOwnershipState { OWNED, LOST }

class PopupSuppressionController(
    private val context: Context,
    private val preferences: AppPreferences,
    private val dao: PopupControlDao,
    private val registry: ChannelSemanticRegistry,
    private val typeResolver: EffectiveNotificationTypeResolver,
    private val access: PopupChannelAccess
) {
    private val writeMutex = Mutex()
    private val generations = LatestGeneration()

    suspend fun observe(
        user: UserHandle,
        packageName: String,
        channelId: String?,
        signature: SemanticSignature,
        reconcileImmediately: Boolean = true
    ) {
        if (channelId.isNullOrBlank() || packageName == context.packageName) return
        if (!preferences.isAppAllowed(packageName)) return
        val changed = registry.observe(userId(user), packageName, channelId, signature)
        if (changed && reconcileImmediately) reconcilePackage(packageName, user)
    }

    suspend fun reconcilePackage(packageName: String, user: UserHandle = android.os.Process.myUserHandle()) {
        if (packageName == context.packageName || !preferences.popupControlEnabled()) return
        val generation = generations.next(packageName)
        writeMutex.withLock {
            if (!generations.isLatest(packageName, generation) || !access.isConnected) return@withLock
            try {
                reconcileLocked(packageName, user)
            } catch (error: SecurityException) {
                Log.e(TAG, "Privileged channel reconciliation denied for $packageName", error)
                if (packageIdentity(packageName) == null) {
                    dao.deleteSemanticsForPackage(packageName)
                    dao.deleteSnapshotsForPackage(packageName)
                    updatePackageDiagnostics(packageName, emptyList())
                } else {
                    PopupControlRuntime.failed(error)
                    preferences.setPopupControlNeedsRepair()
                }
            } catch (error: RuntimeException) {
                Log.e(TAG, "Channel reconciliation failed for $packageName", error)
            }
        }
    }

    suspend fun reconcilePackages(packageNames: Set<String>) {
        val persistedTargets = (dao.getAllSemantics().map { it.packageName to userForUid(it.packageUid) } +
            dao.getAllSnapshots().map { it.packageName to userForUid(it.packageUid) })
            .filter { it.first in packageNames }
            .toSet()
        val currentUserTargets = packageNames.map { it to android.os.Process.myUserHandle() }
        (persistedTargets + currentUserTargets).filterNot { it.first == context.packageName }.forEach {
            reconcilePackage(it.first, it.second)
        }
    }

    suspend fun onChannelModified(
        packageName: String,
        user: UserHandle,
        channel: NotificationChannel,
        modificationType: Int
    ) {
        if (packageName == context.packageName) return
        when (modificationType) {
            android.service.notification.NotificationListenerService.NOTIFICATION_CHANNEL_OR_GROUP_ADDED -> {
                if (preferences.isAppAllowed(packageName)) {
                    registry.registerUnknown(userId(user), packageName, channel.id)
                }
                reconcilePackage(packageName, user)
            }
            android.service.notification.NotificationListenerService.NOTIFICATION_CHANNEL_OR_GROUP_DELETED -> {
                dao.deleteSemantic(userId(user), packageName, channel.id)
                dao.deleteSnapshot(userId(user), packageName, channel.id)
            }
            android.service.notification.NotificationListenerService.NOTIFICATION_CHANNEL_OR_GROUP_UPDATED ->
                reconcilePackage(packageName, user)
        }
    }

    suspend fun restorePackage(packageName: String, user: UserHandle? = null) {
        writeMutex.withLock {
            if (!access.isConnected) return@withLock
            val users = user?.let(::setOf) ?: (
                dao.getAllSnapshots().filter { it.packageName == packageName }.map { userForUid(it.packageUid) } +
                    android.os.Process.myUserHandle()
                ).toSet()
            users.forEach { restoreSnapshotsLocked(packageName, it) }
        }
        markPackageNormal(packageName)
    }

    suspend fun restoreAllAndDisable(packageNames: Set<String>) {
        writeMutex.withLock {
            if (!access.isConnected) {
                PopupControlRuntime.failed(IllegalStateException("Notification listener is disconnected"))
                return
            }
            val snapshots = dao.getAllSnapshots()
            val persistedTargets = snapshots.map { it.packageName to userForUid(it.packageUid) }.toSet()
            val currentTargets = packageNames.map { it to android.os.Process.myUserHandle() }
            (persistedTargets + currentTargets).filterNot { it.first == context.packageName }.forEach {
                restoreSnapshotsLocked(it.first, it.second)
            }
            preferences.setPopupControlDisabledByUser()
        }
        PopupControlRuntime.updateDiagnostics(
            PopupControlRuntime.channelDiagnostics.value.map { it.copy(managed = false) }
        )
    }

    suspend fun deletePackageState(packageName: String) {
        writeMutex.withLock {
            dao.deleteSemanticsForPackage(packageName)
            dao.deleteSnapshotsForPackage(packageName)
        }
        updatePackageDiagnostics(packageName, emptyList())
    }

    suspend fun invalidateSemantics(packageNames: Set<String>) {
        packageNames.filterNot { it == context.packageName }.forEach { packageName ->
            restorePackage(packageName)
            registry.clearPackage(packageName)
        }
        refreshDiagnostics()
    }

    suspend fun verifyPrivilegedAccess() {
        if (!access.isConnected) {
            PopupControlRuntime.disconnected()
            return
        }
        PopupControlRuntime.verifying()
        try {
            access.getChannels(context.packageName, android.os.Process.myUserHandle())
            Log.i(TAG, "Privileged notification-channel probe succeeded")
            PopupControlRuntime.verified()
            if (preferences.popupControlOptInRequestedSync()) {
                preferences.setPopupControlReady()
            }
        } catch (error: SecurityException) {
            Log.e(TAG, "Privileged notification-channel probe denied", error)
            PopupControlRuntime.failed(error)
            preferences.setPopupControlNeedsRepair()
        } catch (error: RuntimeException) {
            Log.e(TAG, "Privileged notification-channel probe failed", error)
            PopupControlRuntime.failed(error)
        }
    }

    private suspend fun reconcileLocked(packageName: String, user: UserHandle) {
        val channels = access.getChannels(packageName, user)
        val channelIds = channels.mapTo(mutableSetOf()) { it.id }
        val semantics = registry.records(userId(user), packageName)
        val snapshots = dao.getSnapshotsForPackage(userId(user), packageName).associateBy { it.channelId }
        (semantics.keys - channelIds).forEach { channelId ->
            dao.deleteSemantic(userId(user), packageName, channelId)
        }
        (snapshots.keys - channelIds).forEach { channelId ->
            dao.deleteSnapshot(userId(user), packageName, channelId)
        }
        val effectiveTypes = typeResolver.getEffectiveTypesFresh(packageName)
        val selected = preferences.isAppAllowed(packageName)
        val diagnostics = mutableListOf<PopupChannelDiagnostic>()

        channels.forEach { channel ->
            val observed = semantics[channel.id].orEmpty()
            val state = if (selected) {
                PopupSuppressionPolicy.semanticState(observed, effectiveTypes)
            } else {
                ChannelSemanticState.NON_TARGET_ONLY
            }
            val snapshot = snapshots[channel.id] ?: recoverSnapshot(packageName, user, channel)
            if (state == ChannelSemanticState.TARGET_ONLY && selected) {
                manageChannel(packageName, user, channel, snapshot)
            } else if (snapshot != null) {
                restoreChannel(packageName, user, channel, snapshot)
            }
            val latest = dao.getSnapshot(userId(user), packageName, channel.id)
            diagnostics += PopupChannelDiagnostic(
                packageName,
                channel.id,
                state,
                latest?.ownershipState == PopupOwnershipState.OWNED.name
            )
        }
        updatePackageDiagnostics(packageName, diagnostics)
    }

    private suspend fun manageChannel(
        packageName: String,
        user: UserHandle,
        channel: NotificationChannel,
        snapshot: PopupChannelSnapshotEntity?
    ) {
        val identity = packageIdentity(packageName) ?: return
        if (snapshot != null) {
            if (!snapshot.matches(identity) || snapshot.channelFingerprint != fingerprint(channel)) {
                dao.deleteSnapshot(userId(user), packageName, channel.id)
                return
            }
            if (snapshot.ownershipState == PopupOwnershipState.LOST.name) return
            if (channel.importance != snapshot.appliedImportance) {
                val stripped = PopupChannelMarker.strip(channel.description)
                if (stripped != channel.description) {
                    channel.description = stripped
                    access.updateChannel(packageName, user, channel)
                }
                dao.upsertSnapshot(snapshot.copy(ownershipState = PopupOwnershipState.LOST.name))
            } else if (PopupChannelMarker.decode(channel.description)?.originalImportance != snapshot.originalImportance) {
                channel.description = PopupChannelMarker.encode(channel.description, snapshot.originalImportance)
                access.updateChannel(packageName, user, channel)
            }
            return
        }

        val original = PopupChannelMarker.decode(channel.description)?.originalImportance ?: channel.importance
        val applied = PopupSuppressionPolicy.appliedImportance(original) ?: return
        val entity = PopupChannelSnapshotEntity(
            userId(user),
            packageName,
            channel.id,
            original,
            applied,
            PopupOwnershipState.OWNED.name,
            fingerprint(channel),
            identity.uid,
            identity.firstInstallTime
        )
        dao.upsertSnapshot(entity)
        channel.description = PopupChannelMarker.encode(channel.description, original)
        channel.importance = applied
        try {
            access.updateChannel(packageName, user, channel)
        } catch (error: Throwable) {
            dao.deleteSnapshot(userId(user), packageName, channel.id)
            throw error
        }
    }

    private suspend fun restoreSnapshotsLocked(packageName: String, user: UserHandle) {
        val channels = access.getChannels(packageName, user).associateBy { it.id }
        val snapshots = dao.getSnapshotsForPackage(userId(user), packageName).associateBy { it.channelId }.toMutableMap()
        channels.values.forEach { channel ->
            if (channel.id !in snapshots) {
                recoverSnapshot(packageName, user, channel)?.let { snapshots[channel.id] = it }
            }
        }
        snapshots.values.forEach { snapshot ->
            channels[snapshot.channelId]?.let { restoreChannel(packageName, user, it, snapshot) }
                ?: dao.deleteSnapshot(userId(user), packageName, snapshot.channelId)
        }
    }

    private suspend fun recoverSnapshot(
        packageName: String,
        user: UserHandle,
        channel: NotificationChannel
    ): PopupChannelSnapshotEntity? {
        val marker = PopupChannelMarker.decode(channel.description) ?: return null
        val identity = packageIdentity(packageName) ?: return null
        val applied = NotificationManager.IMPORTANCE_DEFAULT
        val entity = PopupChannelSnapshotEntity(
            userId = userId(user),
            packageName = packageName,
            channelId = channel.id,
            originalImportance = marker.originalImportance,
            appliedImportance = applied,
            ownershipState = if (channel.importance == applied) PopupOwnershipState.OWNED.name else PopupOwnershipState.LOST.name,
            channelFingerprint = fingerprint(channel),
            packageUid = identity.uid,
            firstInstallTime = identity.firstInstallTime
        )
        dao.upsertSnapshot(entity)
        return entity
    }

    private suspend fun restoreChannel(
        packageName: String,
        user: UserHandle,
        channel: NotificationChannel,
        snapshot: PopupChannelSnapshotEntity
    ) {
        val markerRemoved = PopupChannelMarker.strip(channel.description)
        if (snapshot.ownershipState == PopupOwnershipState.OWNED.name &&
            PopupSuppressionPolicy.canSafelyRestore(channel.importance, snapshot.appliedImportance)
        ) {
            channel.importance = snapshot.originalImportance
            channel.description = markerRemoved
            access.updateChannel(packageName, user, channel)
            dao.deleteSnapshot(userId(user), packageName, channel.id)
        } else {
            if (markerRemoved != channel.description) {
                channel.description = markerRemoved
                access.updateChannel(packageName, user, channel)
            }
            dao.upsertSnapshot(snapshot.copy(ownershipState = PopupOwnershipState.LOST.name))
        }
    }

    private fun packageIdentity(packageName: String): PackageInstallIdentity? = try {
        val info = context.packageManager.getPackageInfo(packageName, 0)
        PackageInstallIdentity(info.applicationInfo?.uid ?: return null, info.firstInstallTime)
    } catch (_: Exception) {
        null
    }

    private fun PopupChannelSnapshotEntity.matches(identity: PackageInstallIdentity): Boolean =
        packageUid == identity.uid && firstInstallTime == identity.firstInstallTime

    private fun userId(user: UserHandle): Int = user.hashCode()

    private fun userForUid(uid: Int): UserHandle = UserHandle.getUserHandleForUid(uid)

    private fun fingerprint(channel: NotificationChannel): String {
        val value = channel.id
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun updatePackageDiagnostics(packageName: String, diagnostics: List<PopupChannelDiagnostic>) {
        val current = PopupControlRuntime.channelDiagnostics.value.filterNot { it.packageName == packageName }
        PopupControlRuntime.updateDiagnostics((current + diagnostics).sortedWith(compareBy({ it.packageName }, { it.channelId })))
    }

    private fun refreshDiagnostics() {
        PopupControlRuntime.updateDiagnostics(PopupControlRuntime.channelDiagnostics.value)
    }

    private fun markPackageNormal(packageName: String) {
        PopupControlRuntime.updateDiagnostics(
            PopupControlRuntime.channelDiagnostics.value.map {
                if (it.packageName == packageName) it.copy(
                    state = ChannelSemanticState.NON_TARGET_ONLY,
                    managed = false
                ) else it
            }
        )
    }
}

internal class LatestGeneration {
    private val values = ConcurrentHashMap<String, Long>()

    fun next(key: String): Long = values.merge(key, 1L, Long::plus) ?: 1L

    fun isLatest(key: String, generation: Long): Boolean = values[key] == generation
}

private const val TAG = "PopupControl"
