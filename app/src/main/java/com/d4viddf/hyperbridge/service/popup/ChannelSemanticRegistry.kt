package com.d4viddf.hyperbridge.service.popup

import android.content.Context
import android.content.pm.PackageManager
import com.d4viddf.hyperbridge.data.db.ChannelSemanticEntity
import com.d4viddf.hyperbridge.data.db.PopupControlDao
import com.d4viddf.hyperbridge.models.NotificationType
import com.d4viddf.hyperbridge.service.SemanticSignature
import kotlinx.coroutines.flow.Flow

data class PackageInstallIdentity(val uid: Int, val firstInstallTime: Long)

class ChannelSemanticRegistry(
    private val context: Context?,
    private val dao: PopupControlDao,
    private val identityOverride: ((String) -> PackageInstallIdentity?)? = null
) {
    suspend fun observe(
        userId: Int,
        packageName: String,
        channelId: String,
        signature: SemanticSignature,
        observedAt: Long = System.currentTimeMillis()
    ): Boolean {
        val identity = packageIdentity(packageName) ?: return false
        val existing = dao.getSemantic(userId, packageName, channelId)
        if (existing != null && !existing.matches(identity)) {
            dao.deleteSemanticsForPackage(packageName)
            dao.deleteSnapshotsForPackage(packageName)
        }
        val current = existing?.takeIf { it.matches(identity) }?.let(::decode).orEmpty()
        if (signature in current) return false
        dao.upsertSemantic(
            ChannelSemanticEntity(
                userId = userId,
                packageName = packageName,
                channelId = channelId,
                semanticSignatures = encode(current + signature),
                lastObservedAt = observedAt,
                packageUid = identity.uid,
                firstInstallTime = identity.firstInstallTime
            )
        )
        return true
    }

    suspend fun registerUnknown(userId: Int, packageName: String, channelId: String) {
        if (dao.getSemantic(userId, packageName, channelId) != null) return
        val identity = packageIdentity(packageName) ?: return
        dao.upsertSemantic(
            ChannelSemanticEntity(
                userId,
                packageName,
                channelId,
                "",
                System.currentTimeMillis(),
                identity.uid,
                identity.firstInstallTime
            )
        )
    }

    suspend fun records(userId: Int, packageName: String): Map<String, Set<SemanticSignature>> {
        val identity = packageIdentity(packageName) ?: return emptyMap()
        val records = dao.getSemanticsForPackage(userId, packageName)
        if (records.any { !it.matches(identity) }) {
            dao.deleteSemanticsForPackage(packageName)
            dao.deleteSnapshotsForPackage(packageName)
            return emptyMap()
        }
        return records.associate { it.channelId to decode(it) }
    }

    suspend fun clearPackage(packageName: String) {
        dao.deleteSemanticsForPackage(packageName)
    }

    fun observeAll(): Flow<List<ChannelSemanticEntity>> = dao.observeAllSemantics()

    suspend fun deleteChannel(userId: Int, packageName: String, channelId: String) {
        dao.deleteSemantic(userId, packageName, channelId)
        dao.deleteSnapshot(userId, packageName, channelId)
    }

    suspend fun deletePackage(packageName: String) {
        dao.deleteSemanticsForPackage(packageName)
        dao.deleteSnapshotsForPackage(packageName)
    }

    fun packageIdentity(packageName: String): PackageInstallIdentity? =
        identityOverride?.invoke(packageName) ?: packageIdentityFromSystem(packageName)

    private fun packageIdentityFromSystem(packageName: String): PackageInstallIdentity? = try {
        val info = context?.packageManager?.getPackageInfo(packageName, 0) ?: return null
        PackageInstallIdentity(info.applicationInfo?.uid ?: return null, info.firstInstallTime)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun ChannelSemanticEntity.matches(identity: PackageInstallIdentity): Boolean =
        packageUid == identity.uid && firstInstallTime == identity.firstInstallTime

    private fun encode(signatures: Set<SemanticSignature>): String = signatures
        .sortedWith(compareBy({ it.primaryType.name }, { it.fallbackType?.name.orEmpty() }))
        .joinToString(";") { "${it.primaryType.name}>${it.fallbackType?.name.orEmpty()}" }

    private fun decode(entity: ChannelSemanticEntity): Set<SemanticSignature> =
        entity.semanticSignatures.split(';').mapNotNull { encoded ->
            if (encoded.isBlank()) return@mapNotNull null
            val parts = encoded.split('>', limit = 2)
            val primary = runCatching { NotificationType.valueOf(parts[0]) }.getOrNull()
                ?: return@mapNotNull null
            val fallback = parts.getOrNull(1)?.takeIf(String::isNotBlank)
                ?.let { runCatching { NotificationType.valueOf(it) }.getOrNull() }
            SemanticSignature(primary, fallback)
        }.toSet()
}
