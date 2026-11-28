package com.mefuturesaibot.app.scanner

import org.junit.Assert.*
import org.junit.Test

class ResearchSignalDisplayTest {
    @Test fun expiredOrFutureRefreshSuppressesDirection() {
        val status = "BTCUSDT · LONG · AGREEMENT · 5"
        assertTrue(ResearchSignalDisplay.freshText(true,status,1000,2999).contains("↑ LONG"))
        for (now in listOf(999L,3000L,9000L)) {
            assertFalse(ResearchSignalDisplay.freshText(true,status,1000,now).contains("LONG"))
        }
        assertFalse(ResearchSignalDisplay.freshText(true,status,0,1000).contains("LONG"))
    }
    @Test fun inconsistentDirectionalEvidenceIsRejected() {
        for (status in listOf("BTCUSDT · LONG · AGREEMENT · 4",
            "BTCUSDT · SHORT · VETO · 5","BTCUSDT · LONG · AGREEMENT")) {
            assertEquals("Master AI · WAIT",ResearchSignalDisplay.text(true,status))
        }
    }
    @Test fun inactiveSuppressesSignal() {
        assertEquals("Master AI · OFFLINE", ResearchSignalDisplay.text(false,"BTCUSDT · LONG · AGREEMENT · 5"))
    }
    @Test fun validPilotRows() {
        val text=ResearchSignalDisplay.text(true,"BTCUSDT · LONG · AGREEMENT · 5\nETHUSDT · WAIT · depth tidak valid")
        assertTrue(text.contains("↑ LONG"))
        assertTrue(text.contains("5 konfirmasi"))
        assertTrue(text.contains("— WAIT"))
        assertTrue(text.contains("bukan order"))
    }
    @Test fun malformedOrDuplicateRowsFailClosed() {
        assertEquals("Master AI · WAIT",ResearchSignalDisplay.text(true,"SOLUSDT · LONG · AGREEMENT · 5"))
        assertEquals("Master AI · WAIT",ResearchSignalDisplay.text(true,"BTCUSDT · LONG · AGREEMENT · 5\nBTCUSDT · SHORT · AGREEMENT · 5"))
    }
}
