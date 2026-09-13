package com.d4viddf.hyperbridge.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "channel_semantics",
    primaryKeys = ["userId", "packageName", "channelId"]
)
data class ChannelSemanticEntity(
    val userId: Int,
    val packageName: String,
    val channelId: String,
    val semanticSignatures: String,
    val lastObservedAt: Long,
    val packageUid: Int,
    val firstInstallTime: Long
)

@Entity(
    tableName = "popup_channel_snapshots",
    primaryKeys = ["userId", "packageName", "channelId"]
)
data class PopupChannelSnapshotEntity(
    val userId: Int,
    val packageName: String,
    val channelId: String,
    val originalImportance: Int,
    val appliedImportance: Int,
    val ownershipState: String,
    val channelFingerprint: String,
    val packageUid: Int,
    val firstInstallTime: Long
)

@Dao
interface PopupControlDao {
    @Query("SELECT * FROM channel_semantics WHERE userId = :userId AND packageName = :packageName")
    suspend fun getSemanticsForPackage(userId: Int, packageName: String): List<ChannelSemanticEntity>

    @Query("SELECT * FROM channel_semantics WHERE userId = :userId AND packageName = :packageName AND channelId = :channelId LIMIT 1")
    suspend fun getSemantic(userId: Int, packageName: String, channelId: String): ChannelSemanticEntity?

    @Query("SELECT * FROM channel_semantics ORDER BY packageName, channelId")
    fun observeAllSemantics(): Flow<List<ChannelSemanticEntity>>

    @Query("SELECT * FROM channel_semantics")
    suspend fun getAllSemantics(): List<ChannelSemanticEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSemantic(entity: ChannelSemanticEntity)

    @Query("DELETE FROM channel_semantics WHERE userId = :userId AND packageName = :packageName AND channelId = :channelId")
    suspend fun deleteSemantic(userId: Int, packageName: String, channelId: String)

    @Query("DELETE FROM channel_semantics WHERE packageName = :packageName")
    suspend fun deleteSemanticsForPackage(packageName: String)

    @Query("SELECT * FROM popup_channel_snapshots WHERE userId = :userId AND packageName = :packageName")
    suspend fun getSnapshotsForPackage(userId: Int, packageName: String): List<PopupChannelSnapshotEntity>

    @Query("SELECT * FROM popup_channel_snapshots")
    suspend fun getAllSnapshots(): List<PopupChannelSnapshotEntity>

    @Query("SELECT * FROM popup_channel_snapshots WHERE userId = :userId AND packageName = :packageName AND channelId = :channelId LIMIT 1")
    suspend fun getSnapshot(userId: Int, packageName: String, channelId: String): PopupChannelSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(entity: PopupChannelSnapshotEntity)

    @Query("DELETE FROM popup_channel_snapshots WHERE userId = :userId AND packageName = :packageName AND channelId = :channelId")
    suspend fun deleteSnapshot(userId: Int, packageName: String, channelId: String)

    @Query("DELETE FROM popup_channel_snapshots WHERE packageName = :packageName")
    suspend fun deleteSnapshotsForPackage(packageName: String)
}
