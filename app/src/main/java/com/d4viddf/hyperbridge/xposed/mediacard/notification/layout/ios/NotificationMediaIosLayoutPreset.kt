package com.d4viddf.hyperbridge.xposed.mediacard.notification.layout.ios

import com.d4viddf.hyperbridge.xposed.mediacard.notification.layout.NotificationMediaLayoutEnvironment
import com.d4viddf.hyperbridge.xposed.mediacard.notification.layout.NotificationMediaLayoutPreset

internal object NotificationMediaIosLayoutPreset : NotificationMediaLayoutPreset {
    override fun apply(environment: NotificationMediaLayoutEnvironment) {
        val progressBarId = environment.ids.mediaProgressBar.takeIf { it != 0 } ?: return

        NotificationMediaIosHeaderLayout.apply(environment)
        NotificationMediaIosProgressLayout.apply(environment, progressBarId)
        NotificationMediaIosActionLayout.apply(environment)
    }
}
