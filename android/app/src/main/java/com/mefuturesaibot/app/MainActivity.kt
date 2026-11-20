package com.mefuturesaibot.app

import android.Manifest
import android.app.AlertDialog
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import android.widget.Switch
import com.mefuturesaibot.app.exchange.ReconnectingWebSocket
import com.mefuturesaibot.app.exchange.SmaSignal
import com.mefuturesaibot.app.exchange.SignalSide

/**
 * MURNI UI: tidak memegang ReconnectingWebSocket sendiri (ada di BotForegroundService).
 *
 * PERBAIKAN (audit 2026-11-10, temuan #3): dulu service AUTO-START tanpa kontrol
 * apa pun begitu app dibuka, dan tidak ada cara menghentikannya dari UI. Sekarang
 * ada tombol Start/Stop eksplisit -- service baru jalan kalau pengguna menekan Start.
 */
class MainActivity : Activity() {
    private lateinit var signalView: TextView
    private lateinit var signalReason: TextView
    private lateinit var signalDetails: TextView
    private lateinit var statusView: TextView
    private lateinit var historyButton: Button
    private lateinit var dataView: TextView
    private val pollHandler = Handler(Looper.getMainLooper())
    private val quoteTime = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss").withZone(ZoneId.systemDefault())
    private val pollIntervalMs = 500L
    // PERBAIKAN audit-mandiri: dulu status "Dihentikan" dideteksi dengan membandingkan
    // isi TextView (statusView.text != "Dihentikan") -- rapuh, karena getText()
    // mengembalikan CharSequence yang tidak selalu String secara internal, bisa gagal
    // cocok meski isinya sama. Sekarang pakai flag boolean biasa, tidak ambigu.
    private var userRequestedStop = false

    private val pollRunnable = object : Runnable {
        override fun run() {
            refreshStatusText()
            pollHandler.postDelayed(this, pollIntervalMs)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val titleView = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#0F172A"))
            setPadding(32, 32, 32, 2)
        }

        val buildView = TextView(this).apply {
            text = getString(R.string.build_identity)
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(Color.parseColor("#475569"))
            setPadding(32, 0, 32, 24)
        }
        signalDetails = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#334155"))
            setPadding(24, 0, 24, 18)
        }
        signalView = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
        }
        signalReason = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(Color.parseColor("#475569"))
            setPadding(32, 4, 32, 20)
        }
        renderSignal(SmaSignal.waitFor("Tekan Start untuk memantau BTC/USDT"))

        statusView = TextView(this).apply {
            text = "Belum dimulai"
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
            setPadding(48, 0, 48, 24)
        }

        historyButton = Button(this).apply {
            visibility = android.view.View.GONE
            setOnClickListener { showCandleHistory() }
        }

        val startButton = Button(this).apply {
            text = "Start"
            setOnClickListener { onStartClicked() }
        }

        val stopButton = Button(this).apply {
            text = "Stop"
            setOnClickListener { onStopClicked() }
        }

        val autoStartSwitch = Switch(this).apply {
            text = "Auto Start setelah reboot"
            isChecked = BotPreferences.isAutoStartEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, enabled ->
                BotPreferences.setAutoStartEnabled(this@MainActivity, enabled)
            }
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(startButton)
            addView(stopButton)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            addView(titleView)
            addView(buildView)
            addView(signalView)
            addView(signalReason)
            addView(signalDetails)
            addView(statusView)
            addView(dataView)
            addView(historyButton)
            addView(autoStartSwitch)
            addView(buttonRow)
        }

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })

        // PERBAIKAN (audit 2026-11-13): dulu izin notifikasi diminta di dalam
        // onStartClicked(), TEPAT SEBELUM startForegroundService() -- padahal
        // requestPermissions() itu asinkron (dialog sistem, tidak menunggu jawaban),
        // jadi Service sempat jalan & memanggil startForeground() SEBELUM pengguna
        // sempat menjawab dialognya. Hasilnya: pertama kali pakai, notifikasi
        // kemungkinan tidak tampil (bukan crash, cuma membingungkan). Sekarang
        // diminta di sini (saat app dibuka), supaya ada jeda alami sebelum
        // pengguna sempat menekan Start.
        ensureNotificationPermission()
    }

    override fun onStart() {
        super.onStart()
        pollHandler.post(pollRunnable)
    }

    override fun onStop() {
        super.onStop()
        pollHandler.removeCallbacks(pollRunnable)
        // SENGAJA TIDAK stopService() di sini -- itu intinya Foreground Service:
        // tetap jalan walau Activity ditutup. Berhenti HANYA lewat tombol Stop.
    }

    private fun onStartClicked() {
        // Catatan: permintaan izin notifikasi sudah dipindah ke onCreate() (lihat
        // komentar di sana) -- tidak diulang di sini supaya tidak memunculkan
        // dialog dua kali kalau pengguna menekan Start berulang.
        userRequestedStop = false
        BotPreferences.setShouldRun(this, true)
        startForegroundService(Intent(this, BotForegroundService::class.java))
    }

    private fun onStopClicked() {
        userRequestedStop = true
        BotPreferences.setShouldRun(this, false)

        if (BotForegroundService.isRunning) {
            startService(
                Intent(this, BotForegroundService::class.java)
                    .setAction(BotForegroundService.ACTION_STOP)
            )
        }

        renderSignal(SmaSignal.waitFor("Pemantauan dihentikan"))
        historyButton.visibility = android.view.View.GONE
        statusView.text = "Dihentikan"
        dataView.text = ""
    }

    private fun refreshStatusText() {
        if (userRequestedStop || !BotForegroundService.isRunning) {
            renderSignal(SmaSignal.waitFor(if (userRequestedStop) "Pemantauan dihentikan" else "Tekan Start untuk memantau BTC/USDT"))
            historyButton.visibility = android.view.View.GONE
            dataView.text = ""
            statusView.text = if (userRequestedStop) "Dihentikan" else "Belum dimulai"
            return
        }
        val history = BotForegroundService.latestHistory
        historyButton.visibility = android.view.View.VISIBLE
        historyButton.isEnabled = history.candles.isNotEmpty()
        val connection = BotForegroundService.latestStatus
        val quote = BotForegroundService.latestQuote
        val elapsed = SystemClock.elapsedRealtime()
        val stale = quote?.isStale(elapsed, BotForegroundService.exchangeClock) ?: true
        val exchangeTime = if (connection == ReconnectingWebSocket.ConnectionStatus.CONNECTED && quote != null && !stale)
            BotForegroundService.exchangeClock.at(elapsed) else null
        val historyStatus = history.statusAt(exchangeTime)
        val lastOpen = history.candles.lastOrNull()?.openTime
        historyButton.text = if (lastOpen == null) historyStatus else
            "$historyStatus · ${history.candles.size} candle 1 menit\nTerakhir ${quoteTime.format(Instant.ofEpochMilli(lastOpen))}"
        statusView.text = describeStatus(connection)
        val signal = when {
            connection != ReconnectingWebSocket.ConnectionStatus.CONNECTED -> SmaSignal.waitFor("Menunggu koneksi pasar")
            quote == null -> SmaSignal.waitFor("Menunggu harga BTC/USDT")
            stale -> SmaSignal.waitFor("Harga pasar kedaluwarsa")
            else -> history.signalAt(exchangeTime)
        }
        renderSignal(signal)
        dataView.text = if (quote == null) {
            "Menunggu harga BTC/USDT"
        } else {
            val updated = quoteTime.format(Instant.ofEpochMilli(quote.eventTimeMs))
            listOfNotNull(
                if (stale) "Data kedaluwarsa" else null,
                "BTC/USDT",
                "Bid (beli tertinggi)  ${quote.bid}",
                "Ask (jual terendah)  ${quote.ask}",
                "Pembaruan bursa  $updated",
            ).joinToString("\n")
        }
    }

    private fun renderSignal(signal: SmaSignal) {
        signalView.text = "Sinyal · ${signal.side.name}"
        signalReason.text = signal.reason
        signalDetails.visibility = if (signal.intervals.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        signalDetails.text = signal.intervals.joinToString("\n\n") { interval ->
            val position = when {
                interval.direction > 0 -> "di atas SMA"
                interval.direction < 0 -> "di bawah SMA"
                else -> "sama dengan SMA"
            }
            val time = quoteTime.format(Instant.ofEpochMilli(interval.closedAtMs))
            "${interval.minutes} menit · $position · $time\n" +
                "Tutup ${interval.close.stripTrailingZeros().toPlainString()}   SMA 20 ${interval.sma.stripTrailingZeros().toPlainString()}"
        }
        val color = when (signal.side) {
            SignalSide.WAIT -> "#744B10"
            SignalSide.BUY -> "#11653F"
            SignalSide.SELL -> "#9D2933"
        }
        signalView.setTextColor(Color.parseColor(color))
    }

    private fun showCandleHistory() {
        val candles = BotForegroundService.latestHistory.candles.asReversed()
        val rows = candles.map { candle ->
            val time = quoteTime.format(Instant.ofEpochMilli(candle.openTime))
            "$time  ·  Tutup ${candle.close}"
        }
        AlertDialog.Builder(this)
            .setTitle("BTC/USDT · Candle tertutup 1 menit")
            .setItems(rows.toTypedArray()) { _, index ->
                val candle = candles[index]
                val time = quoteTime.format(Instant.ofEpochMilli(candle.openTime))
                AlertDialog.Builder(this)
                    .setTitle("BTC/USDT · $time")
                    .setMessage("Buka ${candle.open}\nTinggi ${candle.high}\nRendah ${candle.low}\n" +
                        "Tutup ${candle.close}\nVolume ${candle.volume} BTC")
                    .setPositiveButton("Tutup", null)
                    .show()
            }
            .setPositiveButton("Tutup", null)
            .show()
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE_NOTIFICATIONS)
            }
        }
    }

    private fun describeStatus(status: ReconnectingWebSocket.ConnectionStatus): String = when (status) {
        ReconnectingWebSocket.ConnectionStatus.CONNECTING -> "Menyambungkan..."
        ReconnectingWebSocket.ConnectionStatus.CONNECTED -> "Tersambung \u2713 (via Foreground Service)"
        ReconnectingWebSocket.ConnectionStatus.DISCONNECTED -> "Terputus, mencoba lagi..."
        ReconnectingWebSocket.ConnectionStatus.RECONNECTING -> "Menyambung ulang..."
    }

    companion object {
        private const val REQUEST_CODE_NOTIFICATIONS = 1001
    }
}
