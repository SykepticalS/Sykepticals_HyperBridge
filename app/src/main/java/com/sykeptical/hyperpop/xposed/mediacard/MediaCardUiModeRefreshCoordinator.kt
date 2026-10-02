package com.sykeptical.hyperpop.xposed.mediacard

import com.sykeptical.hyperpop.xposed.mediacard.island.IslandExpandedMediaAmbientFlowHooker
import com.sykeptical.hyperpop.xposed.mediacard.notification.NotificationMediaAmbientFlowHooker
import com.sykeptical.hyperpop.xposed.mediacard.notification.switcher.NotificationMediaSingleCardSwitcherHooker

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
