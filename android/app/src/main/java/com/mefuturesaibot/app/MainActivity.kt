package com.mefuturesaibot.app

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.mefuturesaibot.app.exchange.ReconnectingWebSocket

/**
 * FASE 10 -- Increment ke-2: demo AUTO-RECONNECT nyata (bukan cuma kode
 * tersembunyi). Layar ini konek ke stream publik Binance (bookTicker BTCUSDT,
 * kategori /public sesuai temuan migrasi WS yang sudah diverifikasi di Fase 2 --
 * lihat docs/API_SPEC.md), tidak butuh API Key. Matikan/nyalakan mode pesawat
 * HP Anda untuk melihat status berubah: Terputus -> Menyambung ulang -> Tersambung.
 *
 * PENTING: reconnect ini baru jalan SELAMA APP TERBUKA DI LAYAR (lifecycle
 * onStart/onStop). Supaya tetap reconnect walau app ditutup/layar mati, perlu
 * Foreground Service -- itu increment BERIKUTNYA, belum di build ini.
 */
class MainActivity : Activity() {
    private lateinit var statusView: TextView
    private lateinit var dataView: TextView
    private var socket: ReconnectingWebSocket? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val titleView = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#0F172A"))
            setPadding(48, 160, 48, 24)
        }

        val captionView = TextView(this).apply {
            text = "Demo auto-reconnect -- stream publik Binance (BTCUSDT)"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(48, 0, 48, 24)
        }

        statusView = TextView(this).apply {
            text = "Menyambungkan..."
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#475569"))
            setPadding(48, 0, 48, 16)
        }

        dataView = TextView(this).apply {
            text = ""
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#0EA5E9"))
            setPadding(48, 0, 48, 48)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            addView(titleView)
            addView(captionView)
            addView(statusView)
            addView(dataView)
        }

        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        socket = ReconnectingWebSocket(
            url = "wss://fstream.binance.com/public/stream?streams=btcusdt@bookTicker",
            onStatusChange = { status -> statusView.text = describeStatus(status) },
            onMessage = { text -> dataView.text = text.take(140) },
        ).also { it.connect() }
    }

    override fun onStop() {
        super.onStop()
        socket?.close()
        socket = null
    }

    private fun describeStatus(status: ReconnectingWebSocket.ConnectionStatus): String = when (status) {
        ReconnectingWebSocket.ConnectionStatus.CONNECTING -> "Menyambungkan..."
        ReconnectingWebSocket.ConnectionStatus.CONNECTED -> "Tersambung \u2713"
        ReconnectingWebSocket.ConnectionStatus.DISCONNECTED -> "Terputus, mencoba lagi..."
        ReconnectingWebSocket.ConnectionStatus.RECONNECTING -> "Menyambung ulang..."
    }
}
