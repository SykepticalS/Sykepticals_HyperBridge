package com.sykeptical.hyperpop.service.animation.fingerprint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerprintContactMapperTest {
    @Test
    fun sensorCenterMapsToCanvasCenterAndOutsideIsIgnored() {
        val mapper = FingerprintContactMapper(0f, 0f, 100f, 200f)
        assertTrue(mapper.push(50f, 100f))
        assertEquals(300f, mapper.xAt(0), 0.01f)
        assertEquals(300f, mapper.yAt(0), 0.01f)
        assertFalse(mapper.push(-1f, 100f))
        assertEquals(1, mapper.count)
    }

    @Test
    fun closeSamplesMergeAndTheBufferKeepsSixteen() {
        val mapper = FingerprintContactMapper(0f, 0f, 600f, 600f)
        assertTrue(mapper.push(0f, 0f))
        assertTrue(mapper.push(1f, 1f))
        assertEquals(1, mapper.count)
        for (index in 0 until 20) {
            assertTrue(mapper.push(index * 20f, 40f))
        }
        assertEquals(FingerprintContactMapper.CAPACITY, mapper.count)
        assertTrue(mapper.xAt(0) > 0f)
        mapper.clear()
        assertEquals(0, mapper.count)
        assertEquals(FingerprintContactMapper.NOMINAL_RADIUS, mapper.nominalRadius, 0.01f)
    }
}
