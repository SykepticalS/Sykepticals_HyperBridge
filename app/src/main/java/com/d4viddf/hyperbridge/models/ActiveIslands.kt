package com.d4viddf.hyperbridge.models

data class ActiveIsland(
    val id: Int,
    val type: NotificationType,
    val postTime: Long,
    val sourcePostTime: Long,
    val packageName: String,
    val sourceKey: String,
    val logicalId: String,
    val groupKey: String?,
    val isGroupSummary: Boolean,
    /** Monotonically increases for each posted content generation of this logical Island. */
    val generation: Long,
    // Content Diffing Fields
    val title: String,
    val text: String,
    val subText: String,
    // Used for Deduplication
    val lastContentHash: Int,
    val deleteIntent: android.app.PendingIntent? = null
)
