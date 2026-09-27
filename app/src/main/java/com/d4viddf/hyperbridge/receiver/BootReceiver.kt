package com.d4viddf.hyperbridge.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.util.Log

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.d4viddf.hyperbridge.data.db.AppDatabase

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        
        // 1. Determine Importance
        val isMajor = action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED ||
                action == "android.intent.action.QUICKBOOT_POWERON"

        val isMinor = action == Intent.ACTION_USER_UNLOCKED ||
                action == Intent.ACTION_USER_PRESENT ||
                action == "android.intent.action.USER_SWITCHED"

        val isTest = action == "com.d4viddf.hyperbridge.ACTION_TEST_MIGRATION"

        if (!isMajor && !isMinor && !isTest) return
        if (context.getSystemService(UserManager::class.java)?.isUserUnlocked == false) {
            Log.d("HyperBridge", "Deferring credential-protected migration until user unlock")
            return
        }

        Log.d("HyperBridge", "Trigger event detected: $action")

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // USER_UNLOCKED performs work deferred from LOCKED_BOOT_COMPLETED.
                if (isMajor || action == Intent.ACTION_USER_UNLOCKED || isTest) {
                    Log.d("HyperBridge", "Major trigger: Performing database migration.")
                    AppDatabase.performMigration(context) { progress ->
                        Log.d("HyperBridge", "Migration progress: $progress%")
                    }
                    // Trigger AppPreferences initialization to run SharedPreferences migrations
                    com.d4viddf.hyperbridge.data.AppPreferences(context)
                }
            } catch (e: Exception) {
                Log.e("HyperBridge", "Error during migration in BootReceiver", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
