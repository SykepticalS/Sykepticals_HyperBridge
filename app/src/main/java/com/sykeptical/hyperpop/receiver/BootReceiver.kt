package com.sykeptical.hyperpop.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.util.Log

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.sykeptical.hyperpop.data.db.AppDatabase

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

        val isTest = action == "com.sykeptical.hyperpop.ACTION_TEST_MIGRATION"

        if (!isMajor && !isMinor && !isTest) return
        if (context.getSystemService(UserManager::class.java)?.isUserUnlocked == false) {
            Log.d("HyperPop", "Deferring credential-protected migration until user unlock")
            return
        }

        Log.d("HyperPop", "Trigger event detected: $action")

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // USER_UNLOCKED performs work deferred from LOCKED_BOOT_COMPLETED.
                if (isMajor || action == Intent.ACTION_USER_UNLOCKED || isTest) {
                    Log.d("HyperPop", "Major trigger: Performing database migration.")
                    AppDatabase.performMigration(context) { progress ->
                        Log.d("HyperPop", "Migration progress: $progress%")
                    }
                    // Trigger AppPreferences initialization to run SharedPreferences migrations
                    com.sykeptical.hyperpop.data.AppPreferences(context)
                }
            } catch (e: Exception) {
                Log.e("HyperPop", "Error during migration in BootReceiver", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
