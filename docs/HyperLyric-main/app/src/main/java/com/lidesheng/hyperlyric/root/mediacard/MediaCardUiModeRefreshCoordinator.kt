package com.lidesheng.hyperlyric.root.mediacard

import com.lidesheng.hyperlyric.root.mediacard.island.IslandExpandedMediaAmbientFlowHooker
import com.lidesheng.hyperlyric.root.mediacard.notification.NotificationMediaAmbientFlowHooker
import com.lidesheng.hyperlyric.root.mediacard.notification.switcher.NotificationMediaSingleCardSwitcherHooker

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
