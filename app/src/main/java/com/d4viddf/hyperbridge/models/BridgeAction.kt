package com.d4viddf.hyperbridge.models

import io.github.d4viddf.hyperisland_kit.HyperAction
import io.github.d4viddf.hyperisland_kit.HyperPicture

data class BridgeAction(
    val action: HyperAction,
    val actionImage: HyperPicture? // The icon for this button (if it exists)
)