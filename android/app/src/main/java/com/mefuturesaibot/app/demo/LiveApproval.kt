package com.mefuturesaibot.app.demo

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.mefuturesaibot.app.R
import com.mefuturesaibot.app.account.AccountCheckError
import com.mefuturesaibot.app.account.BinanceAccountCheck
import com.mefuturesaibot.app.account.CredentialVault
import java.util.concurrent.Executors

internal class LiveApproval(private val activity: Activity, private val vault: CredentialVault, private val start: () -> Unit) {
    private val prefs = FuturesVenue.REAL.prefs(activity)
    private val checker = BinanceAccountCheck()
    private val worker = Executors.newSingleThreadExecutor()
    private var busy = false
    @Volatile private var closed = false
    private fun message(text: String) { prefs.edit().putString("status",text).apply() }

    fun begin() {
        if (busy || closed || !vault.exists()) return
        val panel = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val amount = EditText(activity).apply {
            hint = "Alokasi maksimum strategi (USDT)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            isSingleLine = true
        }
        val tested = CheckBox(activity).apply {
            text = "Saya sudah memeriksa hasil simulasi serta menguji entry, TP/SL dan pemulihan koneksi di Demo."
        }
        panel.addView(amount)
        panel.addView(tested)
        val dialog = AlertDialog.Builder(activity).setTitle("Siapkan trading real BTC/USDT")
            .setView(panel).setNegativeButton("Batal",null).setPositiveButton("Periksa akun",null).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val cap = amount.text.toString().trim().replace(',','.').toDoubleOrNull()
            if (cap == null || !cap.isFinite() || cap <= 0) { amount.error = "Masukkan alokasi USDT positif"; return@setOnClickListener }
            if (!tested.isChecked) { tested.error = "Selesaikan pengujian Demo sebelum melanjutkan"; return@setOnClickListener }
            dialog.dismiss()
            verify(cap)
        }
    }

    private fun verify(cap: Double) {
        busy = true
        message("Memeriksa akses akun Binance Futures…")
        worker.execute {
            val result = runCatching {
                val keys = vault.read()
                val access = checker.verify(keys)
                check(access.canTrade && access.futuresKey != false) { "Akses trading Futures belum tersedia." }
                check(access.withdrawals != true) { "Gunakan key trading tanpa izin penarikan." }
                val exchange = DemoExchange(keys, venue = FuturesVenue.REAL)
                exchange.sync()
                check(exchange.oneWay()) { "Akun harus memakai One-way Mode." }
                FuturesVenue.keyId(keys.key)
            }
            activity.runOnUiThread {
                if (closed || activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                busy = false
                result.onSuccess { id ->
                    if (!activity.findViewById<TextView>(R.id.demo_status).isShown) {
                        message("Pemeriksaan selesai · trading real nonaktif")
                    } else confirm(cap,id)
                }.onFailure { error -> message((error as? AccountCheckError)?.explanation
                    ?: (error as? IllegalStateException)?.message ?: "Akun belum terverifikasi. Periksa key dan koneksi.") }
            }
        }
    }

    private fun confirm(cap: Double, keyId: String) {
        message("Akun diperiksa · menunggu persetujuan")
        AlertDialog.Builder(activity).setTitle("Aktifkan trading dengan uang asli?")
            .setMessage("Bot dapat membuka LONG atau SHORT BTC/USDT dengan alokasi maksimum $cap USDT. Risiko terencana hingga 0,5% per posisi. Harga eksekusi saat pasar bergerak cepat dapat membuat kerugian lebih besar. Stop bot meminta penutupan posisi.")
            .setNegativeButton("Batal",null).setPositiveButton("Aktifkan trading real") { _,_ ->
                val armed = synchronized(DemoRunner.entryLock) {
                    if (closed || prefs.contains("intent") || runCatching { FuturesVenue.keyId(vault.read().key) }.getOrNull() != keyId) false
                    else prefs.edit().putString("capital",cap.toString()).putString("consentKey",keyId)
                        .putBoolean("enabled",true).putString("status","Real aktif · menunggu sinyal").commit()
                }
                if (armed) start() else message("Persetujuan belum tersimpan. Periksa akun kembali.")
            }.show()
    }

    fun close() { closed = true; checker.close(); worker.shutdown() }
}
