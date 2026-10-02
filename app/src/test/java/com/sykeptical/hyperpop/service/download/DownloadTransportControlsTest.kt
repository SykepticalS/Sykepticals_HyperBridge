package com.sykeptical.hyperpop.service.download

import com.sykeptical.hyperpop.service.translators.FocusActionIntentTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadTransportControlsTest {
    @Test
    fun playStoreAndChromeLabelsMapToFocusGlyphs() {
        assertEquals(DownloadTransportControls.Glyph.PAUSE, DownloadTransportControls.glyph("Pause"))
        assertEquals(DownloadTransportControls.Glyph.RESUME, DownloadTransportControls.glyph("Resume"))
        assertEquals(DownloadTransportControls.Glyph.CANCEL, DownloadTransportControls.glyph("Cancel"))
        assertEquals(DownloadTransportControls.Glyph.PAUSE, DownloadTransportControls.glyph("暂停"))
        assertEquals(DownloadTransportControls.Glyph.CANCEL, DownloadTransportControls.glyph("取消"))
    }

    @Test
    fun deleteSemanticActionIsCancelEvenWithoutATitle() {
        assertEquals(
            DownloadTransportControls.Glyph.CANCEL,
            DownloadTransportControls.glyph("", isDelete = true),
        )
    }

    @Test
    fun unrelatedActionsStayUntouched() {
        assertNull(DownloadTransportControls.glyph("Open"))
        assertNull(DownloadTransportControls.glyph(""))
    }

    @Test
    fun serviceAndBroadcastIntentsKeepTheirFocusType() {
        assertEquals(
            FocusActionIntentTypes.SERVICE,
            FocusActionIntentTypes.resolve(isBroadcast = false, isForegroundService = true, isService = false),
        )
        assertEquals(
            FocusActionIntentTypes.BROADCAST,
            FocusActionIntentTypes.resolve(isBroadcast = true, isForegroundService = false, isService = true),
        )
        assertEquals(
            FocusActionIntentTypes.ACTIVITY,
            FocusActionIntentTypes.resolve(isBroadcast = false, isForegroundService = false, isService = false),
        )
    }
}
