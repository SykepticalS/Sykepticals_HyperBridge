package com.sykeptical.hyperpop.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sykeptical.hyperpop.R
import com.sykeptical.hyperpop.ui.system.HpGroup
import com.sykeptical.hyperpop.ui.system.HpNavRow
import com.sykeptical.hyperpop.ui.system.HpScaffold
import com.sykeptical.hyperpop.ui.system.HyperPopSpace

data class Library(val name: String, val author: String, val license: String, val url: String)

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    val libs = listOf(
        Library("HyperIsland-ToolKit", "D4vidDf", "Apache 2.0", "https://github.com/D4vidDf/HyperIsland-ToolKit"),
        Library("Jetpack Compose", "Google", "Apache 2.0", "https://developer.android.com/jetpack/compose"),
        Library("Material 3", "Google", "Apache 2.0", "https://m3.material.io/"),
        Library("AndroidX Core", "Google", "Apache 2.0", "https://developer.android.com/jetpack/androidx"),
        Library("AndroidX Activity", "Google", "Apache 2.0", "https://developer.android.com/jetpack/androidx/releases/activity"),
        Library("AndroidX AppCompat", "Google", "Apache 2.0", "https://developer.android.com/jetpack/androidx/releases/appcompat"),
        Library("AndroidX DataStore", "Google", "Apache 2.0", "https://developer.android.com/topic/libraries/architecture/datastore"),
        Library("AndroidX Lifecycle", "Google", "Apache 2.0", "https://developer.android.com/jetpack/androidx/releases/lifecycle"),
        Library("AndroidX Navigation 3", "Google", "Apache 2.0", "https://developer.android.com/jetpack/androidx/releases/navigation"),
        Library("AndroidX Palette", "Google", "Apache 2.0", "https://developer.android.com/develop/ui/views/graphics/palette"),
        Library("AndroidX Room", "Google", "Apache 2.0", "https://developer.android.com/training/data-storage/room"),
        Library("Gson", "Google", "Apache 2.0", "https://github.com/google/gson"),
        Library("Kotlin Coroutines", "JetBrains", "Apache 2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
        Library("Kotlin Serialization", "JetBrains", "Apache 2.0", "https://github.com/Kotlin/kotlinx.serialization"),
        Library("libxposed", "libxposed", "LGPL-3.0", "https://github.com/libxposed/api")
    ).sortedBy { it.name }

    HpScaffold(title = stringResource(R.string.open_source_licenses), onBack = onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = HyperPopSpace.screen)
                .verticalScroll(rememberScrollState()),
        ) {
            HpGroup {
                libs.forEach { lib ->
                    HpNavRow(
                        title = lib.name,
                        subtitle = "${lib.author} • ${lib.license}",
                        onClick = { uriHandler.openUri(lib.url) },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
