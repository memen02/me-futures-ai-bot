package com.mefuturesaibot.app.demo

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import com.mefuturesaibot.app.R
import com.mefuturesaibot.app.account.BinanceCredentials
import com.mefuturesaibot.app.account.CredentialVault

internal class DemoControls(private val activity: Activity, private val start: () -> Unit) {
    val venue = FuturesVenue.selected(activity)
    private val live = venue == FuturesVenue.REAL
    private val vault = CredentialVault(activity, !live)
    private val approval = if (live) LiveApproval(activity,vault,start) else null
    private val prefs = venue.prefs(activity)
    private val toggle = activity.findViewById<Switch>(R.id.demo_enabled)
    private val label = activity.findViewById<TextView>(R.id.demo_status)
    private val keys = activity.findViewById<Button>(R.id.demo_keys)
    private var editor: AlertDialog? = null
    private var rendering = false
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> render() }

    init {
        if (live) {
            activity.findViewById<TextView>(R.id.execution_heading).text = "Binance Futures Real · BTC/USDT"
            activity.findViewById<TextView>(R.id.execution_help).text = "Bot memakai dana asli pada akun khusus One-way. Menonaktifkan bot meminta penutupan sisa posisi milik bot ketika terhubung."
            activity.findViewById<TextView>(R.id.execution_note).text = "Saldo dan performa pada kartu simulasi tetap virtual. Hasil mainnet ditampilkan di bagian Real."
            toggle.text = "Trading otomatis dengan dana asli"
            keys.visibility = View.GONE
        } else {
            activity.findViewById<TextView>(R.id.execution_heading).text = "Binance Futures Demo · BTC/USDT"
            activity.findViewById<TextView>(R.id.execution_help).text = "Gunakan key Binance Demo dari akun One-way. Order memakai saldo virtual bursa."
            activity.findViewById<TextView>(R.id.execution_note).text = "Jurnal Demo mencatat transaksi virtual di Binance Demo."
            toggle.text = "Jalankan dengan saldo Demo"
            keys.visibility = View.VISIBLE
        }
        activity.findViewById<View>(R.id.mainnet_credentials).visibility = if (live) View.VISIBLE else View.GONE
        activity.findViewById<View>(R.id.demo_connection_panel).visibility = if (live) View.GONE else View.VISIBLE
        keys.setOnClickListener { edit() }
        toggle.setOnCheckedChangeListener { _, enabled ->
            if (!rendering && enabled && live) {
                render()
                approval?.begin()
            } else if (!rendering) {
                synchronized(DemoRunner.entryLock) {
                    if (FuturesVenue.selected(activity) != venue) return@setOnCheckedChangeListener
                    prefs.edit().putBoolean("enabled", enabled).putString("status",
                    if (enabled) "Menghubungi Binance Demo…" else "${venue.label} nonaktif · memeriksa sisa posisi").apply() }
                if (enabled) start()
                render()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        render()
    }

    fun render() {
        rendering = true
        val enabled = prefs.getBoolean("enabled",false)
        if (!live) activity.findViewById<TextView>(R.id.demo_key_status).text = if (vault.exists()) "Key Demo tersimpan di HP" else "Belum ada kredensial Demo tersimpan"
        toggle.isChecked = enabled
        toggle.isEnabled = vault.exists() && (enabled || !prefs.contains("intent"))
        keys.isEnabled = !enabled && !prefs.contains("intent")
        label.text = prefs.getString("status", if (vault.exists()) "Key ${venue.label} tersimpan" else "Masukkan key Binance ${venue.label} untuk mulai")
        if (live && enabled) label.append("\nAlokasi maksimum ${prefs.getString("capital","—")} USDT")
        val n = prefs.getInt("n",0)
        if (n > 0) {
            val wins = prefs.getInt("wins",0)
            val losing = prefs.getInt("losing",0)
            fun value(key: String) = prefs.getString(key,"0")!!.toDouble()
            fun average(key: String, count: Int) = if (count == 0) "—" else "%.2f".format(value(key)/count)
            label.append("\nSMA ${venue.label} · $n transaksi · menang ${100 * wins / n}%" +
                "\nNet/trade ${average("net",n)} USDT · drawdown tertutup ${"%.2f".format(value("maxDD"))} USDT" +
                "\nRata-rata laba ${average("profit",wins)} / rugi ${average("loss",losing)} USDT")
        }
        val unrated = prefs.getInt("unrated",0)
        if (unrated > 0) label.append("\n$unrated transaksi belum dinilai")
        rendering = false
    }

    private fun resetDemoSession() {
        val saved = prefs.getString("closedLedger",null)
        val ledger = saved ?: com.mefuturesaibot.app.scanner.TradeLedger(
            incomplete = prefs.getInt("n",0) + prefs.getInt("unrated",0) > 0).json().toString()
        prefs.edit().clear().putString("closedLedger",ledger).apply()
    }

    private fun edit() {
        val form = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        fun field(name: String) = EditText(activity).apply {
            hint = name
            isSingleLine = true
            isSaveEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            imeOptions = imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            form.addView(this)
        }
        val key = field("API Key Demo")
        val secret = field("API Secret Demo")
        val dialog = AlertDialog.Builder(activity).setTitle("Key Binance Futures Demo").setView(form)
            .setPositiveButton("Simpan", null).setNegativeButton("Batal",null)
            .setNeutralButton(if (vault.exists()) "Hapus key" else null) { _,_ ->
                runCatching { vault.remove() }.onSuccess { resetDemoSession() }
                render()
            }.create()
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        editor = dialog
        dialog.setOnDismissListener { key.text.clear(); secret.text.clear(); editor = null }
        dialog.show()
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val k = key.text.toString().trim()
            val s = secret.text.toString().trim()
            if (listOf(k,s).any { it.isEmpty() || it.length > 512 || !it.all { ch -> ch.code in 33..126 } }) {
                secret.error = "Isi pasangan key Demo tanpa spasi"
            } else {
                runCatching { vault.save(BinanceCredentials(k,s)) }.onSuccess {
                    resetDemoSession()
                    dialog.dismiss()
                    render()
                }.onFailure { secret.error = "Penyimpanan terenkripsi gagal" }
            }
        }
    }

    fun close() { editor?.dismiss(); approval?.close(); prefs.unregisterOnSharedPreferenceChangeListener(listener) }
}
