package com.d4viddf.hyperbridge

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.d4viddf.hyperbridge.data.db.AppDatabase
import com.d4viddf.hyperbridge.data.db.ChannelSemanticEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private lateinit var context: Context
    private val databaseName = "popup-migration-test.db"

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migrationFromOnePreservesSettingsAndCreatesPopupTables() = runBlocking {
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { database ->
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `settings` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))"
            )
            database.execSQL("INSERT INTO `settings` (`key`, `value`) VALUES ('setup_complete', 'true')")
            database.version = 1
        }

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
        try {
            assertEquals("true", migrated.settingsDao().getSetting("setup_complete"))
            migrated.popupControlDao().upsertSemantic(
                ChannelSemanticEntity(0, "com.example", "messages", "MESSAGE>", 1L, 10001, 2L)
            )
            assertNotNull(migrated.popupControlDao().getSemantic(0, "com.example", "messages"))
        } finally {
            migrated.close()
        }
    }
}
