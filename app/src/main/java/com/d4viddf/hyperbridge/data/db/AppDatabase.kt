package com.d4viddf.hyperbridge.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AppSetting::class, ChannelSemanticEntity::class, PopupChannelSnapshotEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun popupControlDao(): PopupControlDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            val storageContext = context.createDeviceProtectedStorageContext()
            val dbName = "hyperbridge_db"

            // Migration logic: Move DB from CE to DE storage if it exists in old location
            if (!storageContext.getDatabasePath(dbName).exists()) {
                val oldDb = context.getDatabasePath(dbName)
                if (oldDb.exists()) {
                    storageContext.moveDatabaseFrom(context, dbName)
                }
            }

            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    storageContext,
                    AppDatabase::class.java,
                    dbName
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(false)
                    .build().also { INSTANCE = it }
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `channel_semantics` (
                        `userId` INTEGER NOT NULL,
                        `packageName` TEXT NOT NULL,
                        `channelId` TEXT NOT NULL,
                        `semanticSignatures` TEXT NOT NULL,
                        `lastObservedAt` INTEGER NOT NULL,
                        `packageUid` INTEGER NOT NULL,
                        `firstInstallTime` INTEGER NOT NULL,
                        PRIMARY KEY(`userId`, `packageName`, `channelId`)
                    )""".trimIndent()
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `popup_channel_snapshots` (
                        `userId` INTEGER NOT NULL,
                        `packageName` TEXT NOT NULL,
                        `channelId` TEXT NOT NULL,
                        `originalImportance` INTEGER NOT NULL,
                        `appliedImportance` INTEGER NOT NULL,
                        `ownershipState` TEXT NOT NULL,
                        `channelFingerprint` TEXT NOT NULL,
                        `packageUid` INTEGER NOT NULL,
                        `firstInstallTime` INTEGER NOT NULL,
                        PRIMARY KEY(`userId`, `packageName`, `channelId`)
                    )""".trimIndent()
                )
            }
        }

        fun performMigration(context: Context, onProgress: (Int) -> Unit) {
            val storageContext = context.createDeviceProtectedStorageContext()
            val dbName = "hyperbridge_db"

            if (!storageContext.getDatabasePath(dbName).exists()) {
                val oldDb = context.getDatabasePath(dbName)
                if (oldDb.exists()) {
                    onProgress(10)
                    try {
                        storageContext.moveDatabaseFrom(context, dbName)
                        onProgress(100)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        onProgress(-1) // Error state
                    }
                } else {
                    onProgress(100) // Already in DE or fresh install
                }
            } else {
                onProgress(100) // Already in DE
            }
        }
    }
}
