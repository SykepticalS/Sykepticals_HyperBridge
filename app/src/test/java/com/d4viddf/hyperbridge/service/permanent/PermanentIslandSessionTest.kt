package com.d4viddf.hyperbridge.service.permanent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PermanentIslandSessionTest {
    @Test
    fun closeCompletesOnlyAfterMatchingStart() {
        val session = PermanentIslandSession()
        session.requestClose("app", "source")

        assertNull(session.complete("app"))
        session.markStarted("app")
        val showing = session.complete("app")

        assertEquals("source", showing?.sourceKey)
        assertTrue(session.state is PermanentIslandSession.State.Showing)
    }

    @Test
    fun mismatchedCallbacksCannotCommit() {
        val session = PermanentIslandSession()
        session.requestClose("new.app", "new-source")

        assertNull(session.markStarted("old.app"))
        assertNull(session.complete("old.app"))
        assertTrue(session.state is PermanentIslandSession.State.Closing)
    }

    @Test
    fun duplicateStartOnlyTriggersTheEarlyCommitOnce() {
        val session = PermanentIslandSession()
        val generation = session.requestClose("app", "source")

        assertEquals(generation, session.markStarted("app"))
        assertNull(session.markStarted("app"))
    }

    @Test
    fun aReplacementCloseInvalidatesTheOldGeneration() {
        val session = PermanentIslandSession()
        val first = session.requestClose("first", "one")
        val second = session.requestClose("second", "two")

        assertNotEquals(first, second)
        assertNull(session.complete("first"))
        session.markStarted("second")
        assertEquals(second, session.complete("second")?.generation)
    }

    @Test
    fun interruptionRestoresBlank() {
        val session = PermanentIslandSession()
        session.requestClose("app", "source")

        assertTrue(session.abort("app"))
        assertEquals(PermanentIslandSession.State.Blank, session.state)
        assertFalse(session.abort("app"))
    }

    @Test
    fun reopeningOnlyClearsTheAdoptedPackage() {
        val session = showingSession()

        assertFalse(session.clearIfForeground("other"))
        assertTrue(session.clearIfForeground("app"))
        assertEquals(PermanentIslandSession.State.Blank, session.state)
    }

    @Test
    fun reopeningDuringClosingInvalidatesLateCallbacks() {
        val session = PermanentIslandSession()
        session.requestClose("app", "source")
        session.markStarted("app")

        assertTrue(session.clearIfForeground("app"))
        assertEquals(PermanentIslandSession.State.Blank, session.state)
        assertNull(session.complete("app"))
    }

    @Test
    fun sourceRemovalDuringClosingInvalidatesLateCallbacks() {
        val session = PermanentIslandSession()
        session.requestClose("app", "source")

        assertTrue(session.clearIfSource("source"))
        assertEquals(PermanentIslandSession.State.Blank, session.state)
        assertNull(session.markStarted("app"))
    }

    @Test
    fun activeSourceIsExposedOnlyAfterTheCloseCommits() {
        val session = PermanentIslandSession()
        session.requestClose("app", "source")

        assertNull(session.activeSourceKey())
        assertNull(session.activePackageName())
        assertFalse(session.hasActiveSource())

        session.markStarted("app")
        assertNull(session.activeSourceKey())
        assertNull(session.activePackageName())
        assertFalse(session.hasActiveSource())

        session.complete("app")
        assertEquals("source", session.activeSourceKey())
        assertEquals("app", session.activePackageName())
        assertTrue(session.hasActiveSource())

        session.reset()
        assertNull(session.activeSourceKey())
        assertNull(session.activePackageName())
        assertFalse(session.hasActiveSource())
    }

    @Test
    fun recentsAbortNeverExposesThePendingSource() {
        val session = PermanentIslandSession()
        session.requestClose("app", "source")
        session.markStarted("app")

        assertFalse(session.hasActiveSource())
        assertTrue(session.abort("app"))
        assertEquals(PermanentIslandSession.State.Blank, session.state)
        assertNull(session.activeSourceKey())
    }

    @Test
    fun expansionOrRemovalOnlyClearsTheAdoptedSource() {
        val session = showingSession()

        assertFalse(session.clearIfSource("other"))
        assertTrue(session.clearIfSource("source"))
        assertEquals(PermanentIslandSession.State.Blank, session.state)
    }

    @Test
    fun disablingClearsClosingAndShowingStates() {
        val closing = PermanentIslandSession().apply { requestClose("app", "source") }
        assertTrue(closing.reset())
        assertEquals(PermanentIslandSession.State.Blank, closing.state)

        val showing = showingSession()
        assertTrue(showing.reset())
        assertFalse(showing.reset())
    }

    private fun showingSession() = PermanentIslandSession().apply {
        requestClose("app", "source")
        markStarted("app")
        complete("app")
    }
}
