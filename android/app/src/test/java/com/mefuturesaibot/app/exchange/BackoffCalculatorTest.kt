package com.mefuturesaibot.app.exchange

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Catatan: test ini memanggil ReconnectingWebSocket.computeBackoffMs lewat companion
 * object SAJA -- tidak pernah membuat instance ReconnectingWebSocket (yang di
 * constructor-nya memanggil Handler(Looper.getMainLooper()), tidak tersedia di
 * lingkungan JVM test biasa tanpa Robolectric). Mengakses companion object TIDAK
 * memicu constructor instance, jadi ini aman dijalankan sebagai unit test JVM murni.
 */
class BackoffCalculatorTest {

    @Test
    fun backoff_di_attempt_0_sekitar_baseMs_plus_jitter() {
        val delay = ReconnectingWebSocket.computeBackoffMs(0, baseMs = 1000, maxMs = 30_000)
        assertTrue("delay=$delay harus di rentang [1000, 1300]", delay in 1000..1300)
    }

    @Test
    fun backoff_naik_seiring_attempt_bertambah() {
        val a0 = ReconnectingWebSocket.computeBackoffMs(0, baseMs = 1000, maxMs = 30_000)
        val a3 = ReconnectingWebSocket.computeBackoffMs(3, baseMs = 1000, maxMs = 30_000)
        assertTrue("backoff attempt=3 ($a3) harus lebih besar dari attempt=0 ($a0)", a3 > a0)
    }

    @Test
    fun backoff_dibatasi_maxMs_meski_attempt_sangat_besar() {
        val delay = ReconnectingWebSocket.computeBackoffMs(20, baseMs = 1000, maxMs = 30_000)
        assertTrue("delay=$delay tidak boleh melebihi maxMs+jitter", delay <= (30_000 * 1.3).toLong())
    }
}
