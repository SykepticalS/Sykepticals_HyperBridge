package com.sykeptical.hyperbridge.service.recording

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenRecordingControlBackendInstrumentedTest {
    @Test
    fun ordinaryAppUidCanProbeExportedXiaomiRecorderTool() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val component = ComponentName(
            ScreenRecordingClassifier.PACKAGE_NAME,
            ScreenRecordingToolProtocol.SERVICE_CLASS
        )
        val serviceExists = try {
            context.packageManager.getServiceInfo(
                component,
                PackageManager.ComponentInfoFlags.of(0)
            )
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        assumeTrue("Xiaomi recorder tool service is not installed", serviceExists)

        val capabilities = XiaomiScreenRecordingControlBackend(context).probeCapabilities()
        assertTrue("Ordinary app UID could not verify Xiaomi Stop capability", capabilities.canStop)
    }
}
