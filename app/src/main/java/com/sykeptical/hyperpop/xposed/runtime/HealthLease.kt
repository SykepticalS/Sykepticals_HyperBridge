package com.sykeptical.hyperpop.xposed.runtime

object HealthLease {
    fun fresh(nowElapsed: Long, heartbeatElapsed: Long, leaseMillis: Long): Boolean =
        heartbeatElapsed > 0L && nowElapsed >= heartbeatElapsed && nowElapsed - heartbeatElapsed <= leaseMillis
}
