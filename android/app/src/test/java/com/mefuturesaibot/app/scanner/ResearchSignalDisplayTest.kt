package com.mefuturesaibot.app.scanner

import org.junit.Assert.*
import org.junit.Test

class ResearchSignalDisplayTest {
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
