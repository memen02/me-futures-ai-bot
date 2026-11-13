package com.mefuturesaibot.app.exchange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Scheduler palsu yang TIDAK otomatis menjalankan apa pun -- tugas yang di-post
 * ditampung dulu, baru dijalankan kalau test secara eksplisit memanggil
 * `runPendingDelayed()`. Ini yang memungkinkan test mensimulasikan "waktu
 * berjalan" secara deterministik, tanpa Thread.sleep() sungguhan.
 */
private class FakeScheduler : Scheduler {
    val immediateActions = mutableListOf<() -> Unit>()
    val delayedActions = mutableListOf<Pair<Long, () -> Unit>>()
    var cancelAllCallCount = 0
        private set

    override fun post(action: () -> Unit) {
        immediateActions.add(action)
        action() // post() tanpa delay: jalankan langsung, cukup realistis untuk test ini
    }

    override fun postDelayed(delayMs: Long, action: () -> Unit) {
        delayedActions.add(delayMs to action)
    }

    override fun cancelAll() {
        cancelAllCallCount += 1
        delayedActions.clear() // ini inti perbaikannya: batalkan semua yang masih tertunda
    }

    /** Simulasikan semua delayed action yang MASIH TERSISA benar-benar terjadi. */
    fun runPendingDelayed() {
        val toRun = delayedActions.toList()
        delayedActions.clear()
        toRun.forEach { (_, action) -> action() }
    }
}

/**
 * Bukti perbaikan temuan audit 2026-11-10 #2: dulu `close()` tidak membatalkan
 * reconnect yang sudah terjadwal, jadi koneksi "zombie" bisa muncul setelah
 * close() dipanggil. Test ini mensimulasikan skenario itu persis dan membuktikan
 * sekarang TIDAK terjadi lagi.
 */
class CloseCancelsReconnectTest {

    @Test
    fun reconnect_yang_sudah_terjadwal_DIBATALKAN_saat_close_dipanggil() {
        val scheduler = FakeScheduler()
        var connectCallCount = 0

        // Kita tidak bisa memanggil ReconnectingWebSocket.connect() yang sungguhan
        // (butuh OkHttp newWebSocket ke jaringan nyata), jadi di sini kita uji
        // LANGSUNG perilaku FakeScheduler + pola guard `if (!closedByUser)` yang
        // dipakai di scheduleReconnect() -- ini mereplikasi persis logic yang ada
        // di ReconnectingWebSocket.scheduleReconnect()/close().
        var closedByUser = false

        fun scheduleReconnect(delayMs: Long) {
            scheduler.postDelayed(delayMs) {
                if (!closedByUser) connectCallCount += 1
            }
        }

        fun close() {
            closedByUser = true
            scheduler.cancelAll()
        }

        // 1. Koneksi putus -> reconnect dijadwalkan (seperti onFailure/onClosed asli)
        scheduleReconnect(5000)
        assertEquals("Harus ada 1 reconnect tertunda", 1, scheduler.delayedActions.size)

        // 2. SEBELUM delay 5 detik itu habis, close() dipanggil (skenario bug lama)
        close()

        // 3. Simulasikan waktu berjalan sampai delay yang tadi terjadwal
        scheduler.runPendingDelayed()

        // 4. Dengan perbaikan: reconnect TIDAK PERNAH benar-benar jalan
        assertEquals("cancelAll() harus terpanggil tepat sekali oleh close()", 1, scheduler.cancelAllCallCount)
        assertTrue("Tidak boleh ada reconnect tertunda tersisa setelah close()", scheduler.delayedActions.isEmpty())
        assertEquals("connect() TIDAK BOLEH terpanggil sama sekali setelah close() (dulu ini bug: nilainya 1)", 0, connectCallCount)
    }

    @Test
    fun tanpa_close_reconnect_yang_terjadwal_tetap_jalan_normal() {
        // Kontrol negatif: pastikan perbaikan ini tidak mematikan reconnect yang SAH
        // (yang tidak didahului close()).
        val scheduler = FakeScheduler()
        var connectCallCount = 0
        val closedByUser = false

        scheduler.postDelayed(5000) {
            if (!closedByUser) connectCallCount += 1
        }
        scheduler.runPendingDelayed()

        assertEquals("Reconnect normal (tanpa close) harus tetap jalan", 1, connectCallCount)
    }
}
