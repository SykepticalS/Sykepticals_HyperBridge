package com.d4viddf.hyperbridge.service.popup

import android.app.NotificationChannel
import android.content.Context
import android.os.UserHandle
import android.util.Log
import com.d4viddf.hyperbridge.data.AppPreferences
import com.d4viddf.hyperbridge.data.db.PopupControlDao
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface PopupChannelAccess {
    val isConnected: Boolean
    fun getChannels(packageName: String, user: UserHandle): List<NotificationChannel>
    fun updateChannel(packageName: String, user: UserHandle, channel: NotificationChannel)
}

/**
 * One-way upgrade migration for releases that lowered source-channel importance.
 * It never manages new channels and is safe to run repeatedly until every owned snapshot is restored.
 */
class LegacyPopupChannelMigration(
    private val context: Context,
    private val preferences: AppPreferences,
    private val dao: PopupControlDao,
    private val access: PopupChannelAccess,
) {
    private val mutex = Mutex()

    suspend fun restoreAll() = mutex.withLock {
        if (!access.isConnected) return@withLock
        val snapshots = dao.getAllSnapshots()
        snapshots.groupBy { it.userId to it.packageName }.forEach { (target, records) ->
            val (userId, packageName) = target
            val identity = runCatching { context.packageManager.getPackageInfo(packageName, 0) }.getOrNull()
            val uid = identity?.applicationInfo?.uid
            val installedAt = identity?.firstInstallTime
            if (uid == null || installedAt == null) {
                dao.deleteSnapshotsForPackage(packageName)
                return@forEach
            }
            val user = UserHandle.getUserHandleForUid(userId * 100000)
            val channels = runCatching { access.getChannels(packageName, user).associateBy { it.id } }
                .getOrElse {
                    Log.w(TAG, "Unable to restore channels for $packageName", it)
                    return@forEach
                }
            records.forEach { snapshot ->
                val channel = channels[snapshot.channelId]
                if (channel == null || snapshot.packageUid != uid || snapshot.firstInstallTime != installedAt) {
                    dao.deleteSnapshot(userId, packageName, snapshot.channelId)
                    return@forEach
                }
                val stripped = PopupChannelMarker.strip(channel.description)
                val markerChanged = stripped != channel.description
                val owned = snapshot.ownershipState == PopupOwnershipState.OWNED.name &&
                    channel.importance == snapshot.appliedImportance
                if (owned) channel.importance = snapshot.originalImportance
                if (markerChanged) channel.description = stripped
                if (owned || markerChanged) {
                    runCatching { access.updateChannel(packageName, user, channel) }
                        .onFailure {
                            Log.w(TAG, "Channel restoration failed for $packageName/${channel.id}", it)
                            return@forEach
                        }
                }
                dao.deleteSnapshot(userId, packageName, snapshot.channelId)
            }
        }
        if (dao.getAllSnapshots().isEmpty()) preferences.setPopupControlDisabledByUser()
    }

    private enum class PopupOwnershipState { OWNED, LOST }
    companion object { private const val TAG = "LegacyPopupMigration" }
}
