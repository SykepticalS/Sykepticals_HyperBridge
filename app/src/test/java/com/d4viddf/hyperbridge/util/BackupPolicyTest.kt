package com.d4viddf.hyperbridge.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPolicyTest {
    @Test
    fun popupOperationalStateIsNeverPortable() {
        assertTrue(BackupPolicy.isDeviceLocalOperationalKey("popup_control_enabled"))
        assertTrue(BackupPolicy.isDeviceLocalOperationalKey("popup_semantic_rules_fingerprint"))
        assertFalse(BackupPolicy.isDeviceLocalOperationalKey("global_notification_types"))
        assertFalse(BackupPolicy.isDeviceLocalOperationalKey("config_com.example"))
    }
}
