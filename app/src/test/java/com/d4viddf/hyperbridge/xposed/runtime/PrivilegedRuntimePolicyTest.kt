package com.d4viddf.hyperbridge.xposed.runtime

import com.d4viddf.hyperbridge.island.backend.IslandProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivilegedRuntimePolicyTest {
    @Test fun protocolCompatibilityIsExact() {
        assertTrue(IslandProtocol.compatible(IslandProtocol.VERSION))
        assertFalse(IslandProtocol.compatible(IslandProtocol.VERSION + 1))
    }

    @Test fun healthLeaseExpiresAndRejectsFutureHeartbeat() {
        assertTrue(HealthLease.fresh(50_000, 20_000, 45_000))
        assertFalse(HealthLease.fresh(70_001, 20_000, 45_000))
        assertFalse(HealthLease.fresh(10_000, 20_000, 45_000))
    }

    @Test fun restartRequirementsMergeWithoutBroadeningHotReload() {
        val merged = ApplyRequirement.forSetting("focus_whitelist_hook")
            .merge(ApplyRequirement.forSetting("focus_auth_hook"))
        assertEquals(
            ApplyRequirement.Restart(setOf(RestartTarget.SYSTEM_UI, RestartTarget.XMSF)),
            merged,
        )
        assertEquals(ApplyRequirement.HotReload, ApplyRequirement.forSetting("theme_color"))
    }

}
