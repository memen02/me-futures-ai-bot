package com.mefuturesaibot.app.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ResearchPilotLifecycleTest {
    private class Session : ResearchPilotSession {
        var display = "WAIT"
        var closes = 0
        override fun status() = display
        override fun close() { closes++ }
    }

    @Test fun scannerAloneOpensNoResearchSessionAndRepeatedActiveTicksReuseOne() {
        val sessions = mutableListOf<Session>()
        val owner = ResearchPilotLifecycle({ 0L }) { Session().also(sessions::add) }
        repeat(3) { assertEquals("OFFLINE", owner.status(false)) }
        assertEquals(0, sessions.size)
        repeat(3) { assertEquals("WAIT", owner.status(true)) }
        assertEquals(1, sessions.size)
        owner.close()
        assertEquals(1, sessions.single().closes)
    }

    @Test fun restartBeginsInWaitAndLateResultsFromStoppedActivationCannotPublish() {
        val sessions = mutableListOf<Session>()
        val owner = ResearchPilotLifecycle({ 0L }) { Session().also(sessions::add) }
        owner.status(true)
        val old = sessions.single()
        old.display = "LONG"
        assertEquals("LONG", owner.status(true))
        repeat(2) { assertEquals("OFFLINE", owner.status(false)) }
        assertEquals(1, old.closes)
        old.display = "SHORT"
        assertEquals("WAIT", owner.status(true))
        assertEquals(2, sessions.size)
        repeat(2) { owner.close() }
        assertEquals(1, sessions.last().closes)
        assertEquals("OFFLINE", owner.status(true))
        assertEquals(2, sessions.size)
    }

    @Test fun failedStartBacksOffWithoutStoppingScannerAndCanRecover() {
        var attempts = 0
        var elapsed = 0L
        val owner = ResearchPilotLifecycle({ elapsed }) {
            attempts++
            if (attempts == 1) error("failed connection")
            Session()
        }
        val first = owner.status(true)
        elapsed = 29_999L
        assertEquals(first, owner.status(true))
        assertEquals(1, attempts)
        elapsed++
        assertEquals("WAIT", owner.status(true))
        assertEquals(2, attempts)
        owner.close()
    }
}
