package com.d4viddf.hyperbridge.service.animation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CenteredExitSessionTest {
    @Test
    fun matchingRequestStartAndEndCompletesOnce() {
        val session = CenteredExitSession()
        val generation = session.arm("app")

        assertTrue(session.markStarted("app") == generation)
        assertTrue(session.complete("app") == generation)
        assertNull(session.complete("app"))
    }

    @Test
    fun endWithoutStartCannotReveal() {
        val session = CenteredExitSession()
        session.arm("app")

        assertNull(session.complete("app"))
    }

    @Test
    fun stalePackageCallbacksCannotReveal() {
        val session = CenteredExitSession()
        session.arm("new.app")

        assertNull(session.markStarted("old.app"))
        assertNull(session.complete("old.app"))
    }

    @Test
    fun newerRequestInvalidatesOlderGeneration() {
        val session = CenteredExitSession()
        val first = session.arm("first")
        val second = session.arm("second")

        assertNotEquals(first, second)
        assertNull(session.markStarted("first"))
        assertTrue(session.markStarted("second") == second)
    }

    @Test
    fun recentsAbortDropsPendingReveal() {
        val session = CenteredExitSession()
        session.arm("app")
        session.markStarted("app")

        assertTrue(session.abort("app"))
        assertNull(session.complete("app"))
        assertFalse(session.abort("app"))
    }
}
