package com.sykeptical.hyperpop.service.animation.fingerprint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AcquiredCodeClassifierTest {
    @Test
    fun frameworkCodes() {
        assertEquals(AcquiredKind.Good, AcquiredCodeClassifier.classify(0))
        assertEquals(AcquiredKind.Help, AcquiredCodeClassifier.classify(2))
        assertEquals(AcquiredKind.Help, AcquiredCodeClassifier.classify(5))
        assertEquals(AcquiredKind.Down, AcquiredCodeClassifier.classify(100))
        assertEquals(AcquiredKind.Up, AcquiredCodeClassifier.classify(101))
        assertEquals(AcquiredKind.Ignore, AcquiredCodeClassifier.classify(6))
    }

    @Test
    fun vendorCodesRawAndOffset() {
        assertEquals(AcquiredKind.VendorDown, AcquiredCodeClassifier.classify(22))
        assertEquals(AcquiredKind.VendorDown, AcquiredCodeClassifier.classify(1022))
        assertEquals(AcquiredKind.VendorUp, AcquiredCodeClassifier.classify(23))
        assertEquals(AcquiredKind.VendorFail, AcquiredCodeClassifier.classify(44))
        assertEquals(AcquiredKind.VendorPostFail, AcquiredCodeClassifier.classify(201))
        assertEquals(AcquiredKind.VendorOther, AcquiredCodeClassifier.classify(24))
        assertTrue(AcquiredCodeClassifier.isFingerDown(AcquiredKind.VendorDown))
        assertTrue(AcquiredCodeClassifier.isFingerUp(AcquiredKind.Up))
        assertFalse(AcquiredCodeClassifier.isFingerDown(AcquiredKind.Help))
    }

    @Test
    fun lockoutErrors() {
        assertTrue(AcquiredCodeClassifier.isLockoutError(7))
        assertTrue(AcquiredCodeClassifier.isLockoutError(9))
        assertFalse(AcquiredCodeClassifier.isLockoutError(5))
    }
}
