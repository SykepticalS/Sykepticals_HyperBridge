package com.d4viddf.hyperbridge.xposed.runtime

object HealthLease {
    fun fresh(nowElapsed: Long, heartbeatElapsed: Long, leaseMillis: Long): Boolean =
        heartbeatElapsed > 0L && nowElapsed >= heartbeatElapsed && nowElapsed - heartbeatElapsed <= leaseMillis
}
