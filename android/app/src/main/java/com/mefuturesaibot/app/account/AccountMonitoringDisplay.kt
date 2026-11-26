package com.mefuturesaibot.app.account

import android.app.Activity
import android.os.SystemClock
import com.mefuturesaibot.app.demo.FuturesVenue
import com.mefuturesaibot.app.security.UiAccess

/** Foreground-only display monitoring; never supplies execution balances. */
internal class AccountMonitoringDisplay(private val activity: Activity) : AutoCloseable {
    private val demo by lazy { CredentialVault(activity.applicationContext, demo = true) }
    private val real by lazy { CredentialVault(activity.applicationContext) }
    private fun vault(venue: FuturesVenue) = if (venue == FuturesVenue.DEMO) demo else real
    private val coordinator = AccountMonitoringCoordinator(
        currentRevision = { vault(it).revision() }
    )
    private var venue: FuturesVenue? = null
    private var session: Long? = null
    private var nextAttempt = 0L
    private var closed = false
    private companion object { const val RETRY_MS = 15000L }

    fun refresh(target: FuturesVenue): String {
        if (closed || !UiAccess.allowed(activity)) {
            stop()
            return "Monitoring akun dijeda"
        }
        val currentSession = UiAccess.session()
        if (venue != target || session != currentSession) {
            stop()
            venue = target
            session = currentSession
        }
        val label = if (target == FuturesVenue.DEMO) "Binance DEMO" else "Binance REAL"
        return try {
            if (!coordinator.refresh()) {
                val now = SystemClock.elapsedRealtime()
                if (now < nextAttempt) return "$label · Menunggu koneksi"
                nextAttempt = now + RETRY_MS
                val source = vault(target)
                if (!source.exists()) return "$label · API belum tersimpan"
                if (!coordinator.start(target, source.readVersioned())) {
                    return "$label · Menunggu sesi baru"
                }
            }
            val snapshot = coordinator.snapshot()
            if (!UiAccess.allowed(activity, currentSession) || snapshot?.venue != target) {
                coordinator.stop()
                nextAttempt = SystemClock.elapsedRealtime() + RETRY_MS
                return "$label · Saldo belum tersedia"
            }
            val balance = snapshot.balance
            if (balance == null) "$label · ${snapshot.session.state.name} · Saldo belum tersedia"
            else "$label · Saldo ${balance.wallet.toPlainString()} USDT\n" +
                "Ekuitas ${balance.equity.toPlainString()} · Tersedia ${balance.available.toPlainString()}\n" +
                "PnL berjalan ${balance.unrealizedPnl.toPlainString()} USDT"
        } catch (_: Exception) {
            runCatching { coordinator.stop() }
            nextAttempt = SystemClock.elapsedRealtime() + RETRY_MS
            "$label · Monitoring belum tersedia"
        }
    }

    fun stop() {
        venue = null
        session = null
        nextAttempt = 0L
        if (!closed) runCatching { coordinator.stop() }
    }

    override fun close() {
        if (closed) return
        stop()
        closed = true
        coordinator.close()
    }
}
