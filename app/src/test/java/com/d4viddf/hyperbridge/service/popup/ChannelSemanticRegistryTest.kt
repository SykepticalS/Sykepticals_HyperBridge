package com.d4viddf.hyperbridge.service.popup

import com.d4viddf.hyperbridge.data.db.ChannelSemanticEntity
import com.d4viddf.hyperbridge.data.db.PopupChannelSnapshotEntity
import com.d4viddf.hyperbridge.data.db.PopupControlDao
import com.d4viddf.hyperbridge.models.NotificationType
import com.d4viddf.hyperbridge.service.SemanticSignature
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelSemanticRegistryTest {
    private val identity = PackageInstallIdentity(10001, 42L)

    @Test
    fun firstObservationPersistsAndRepeatedObservationDeduplicates() = runBlocking {
        val dao = FakePopupControlDao()
        val registry = ChannelSemanticRegistry(null, dao) { identity }
        val signature = SemanticSignature(NotificationType.MESSAGE, NotificationType.STANDARD)

        assertTrue(registry.observe(0, "app", "channel", signature, 10L))
        assertFalse(registry.observe(0, "app", "channel", signature, 20L))
        assertEquals(setOf(signature), registry.records(0, "app")["channel"])
        assertEquals(10L, dao.getSemantic(0, "app", "channel")?.lastObservedAt)
    }

    @Test
    fun reinstallIdentityPurgesOldSemanticAndOwnershipState() = runBlocking {
        val dao = FakePopupControlDao()
        var currentIdentity = identity
        val registry = ChannelSemanticRegistry(null, dao) { currentIdentity }
        registry.observe(0, "app", "channel", SemanticSignature(NotificationType.MESSAGE))
        dao.upsertSnapshot(PopupChannelSnapshotEntity(0, "app", "channel", 4, 3, "OWNED", "x", 10001, 42L))

        currentIdentity = PackageInstallIdentity(10002, 84L)
        registry.observe(0, "app", "channel", SemanticSignature(NotificationType.CALL))

        assertEquals(setOf(SemanticSignature(NotificationType.CALL)), registry.records(0, "app")["channel"])
        assertTrue(dao.getSnapshotsForPackage(0, "app").isEmpty())
    }
}

private class FakePopupControlDao : PopupControlDao {
    private val semantics = linkedMapOf<Triple<Int, String, String>, ChannelSemanticEntity>()
    private val snapshots = linkedMapOf<Triple<Int, String, String>, PopupChannelSnapshotEntity>()
    private val flow = MutableStateFlow<List<ChannelSemanticEntity>>(emptyList())

    override suspend fun getSemanticsForPackage(userId: Int, packageName: String) =
        semantics.values.filter { it.userId == userId && it.packageName == packageName }

    override suspend fun getSemantic(userId: Int, packageName: String, channelId: String) =
        semantics[Triple(userId, packageName, channelId)]

    override fun observeAllSemantics(): Flow<List<ChannelSemanticEntity>> = flow

    override suspend fun getAllSemantics() = semantics.values.toList()

    override suspend fun upsertSemantic(entity: ChannelSemanticEntity) {
        semantics[Triple(entity.userId, entity.packageName, entity.channelId)] = entity
        flow.value = semantics.values.toList()
    }

    override suspend fun deleteSemantic(userId: Int, packageName: String, channelId: String) {
        semantics.remove(Triple(userId, packageName, channelId))
        flow.value = semantics.values.toList()
    }

    override suspend fun deleteSemanticsForPackage(packageName: String) {
        semantics.entries.removeAll { it.value.packageName == packageName }
        flow.value = semantics.values.toList()
    }

    override suspend fun getSnapshotsForPackage(userId: Int, packageName: String) =
        snapshots.values.filter { it.userId == userId && it.packageName == packageName }

    override suspend fun getAllSnapshots() = snapshots.values.toList()

    override suspend fun getSnapshot(userId: Int, packageName: String, channelId: String) =
        snapshots[Triple(userId, packageName, channelId)]

    override suspend fun upsertSnapshot(entity: PopupChannelSnapshotEntity) {
        snapshots[Triple(entity.userId, entity.packageName, entity.channelId)] = entity
    }

    override suspend fun deleteSnapshot(userId: Int, packageName: String, channelId: String) {
        snapshots.remove(Triple(userId, packageName, channelId))
    }

    override suspend fun deleteSnapshotsForPackage(packageName: String) {
        snapshots.entries.removeAll { it.value.packageName == packageName }
    }
}
