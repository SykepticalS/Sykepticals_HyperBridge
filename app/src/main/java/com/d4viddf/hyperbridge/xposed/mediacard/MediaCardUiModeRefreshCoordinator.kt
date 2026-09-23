package com.d4viddf.hyperbridge.xposed.mediacard

import com.d4viddf.hyperbridge.xposed.mediacard.island.IslandExpandedMediaAmbientFlowHooker
import com.d4viddf.hyperbridge.xposed.mediacard.notification.NotificationMediaAmbientFlowHooker
import com.d4viddf.hyperbridge.xposed.mediacard.notification.switcher.NotificationMediaSingleCardSwitcherHooker

internal object MediaCardUiModeRefreshCoordinator {
    fun refresh() {
        NotificationMediaSingleCardSwitcherHooker.runWithUiModeRefreshGuard {
            val extraControllers =
                NotificationMediaSingleCardSwitcherHooker.extraCardControllers()
            NotificationMediaAmbientFlowHooker.refreshForUiMode(extraControllers)
            NotificationMediaSingleCardSwitcherHooker.refreshForUiMode()
        }
        IslandExpandedMediaAmbientFlowHooker.refreshForUiMode()
    }
}
