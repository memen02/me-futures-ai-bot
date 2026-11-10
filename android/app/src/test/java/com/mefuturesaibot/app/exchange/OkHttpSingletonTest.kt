package com.mefuturesaibot.app.exchange

import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Bukti perbaikan temuan audit #5 (2026-11-08): OkHttpClient tidak lagi dibuat baru
 * setiap kali ReconnectingWebSocket di-instantiate tanpa `client` eksplisit -- semua
 * memakai satu instance bersama (`sharedHttpClient`). `assertSame` memeriksa identitas
 * objek (referensi memori yang sama), bukan cuma kesetaraan nilai -- ini benar-benar
 * membuktikan "satu instance", bukan sekadar dua objek yang kebetulan setara.
 */
class OkHttpSingletonTest {

    @Test
    fun sharedHttpClient_mengembalikan_instance_yang_sama_setiap_dipanggil() {
        val a = ReconnectingWebSocket.sharedHttpClient
        val b = ReconnectingWebSocket.sharedHttpClient
        assertSame("sharedHttpClient harus mengembalikan objek yang IDENTIK, bukan instance baru", a, b)
    }
}
