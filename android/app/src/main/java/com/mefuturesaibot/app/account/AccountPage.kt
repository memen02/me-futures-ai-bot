package com.mefuturesaibot.app.account

import com.mefuturesaibot.app.security.UiAccess

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.mefuturesaibot.app.R
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

internal class AccountPage(
    private val activity: Activity,
    private val vault: CredentialVault = CredentialVault(activity),
    private val checker: BinanceAccountCheck = BinanceAccountCheck(),
    private val credentialsChanged: () -> Unit = {},
) {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val key = activity.findViewById<EditText>(R.id.account_key)
    private val secret = activity.findViewById<EditText>(R.id.account_secret)
    private val status = activity.findViewById<TextView>(R.id.account_connection)
    private val info = activity.findViewById<TextView>(R.id.account_access)
    private val buttons = listOf(R.id.account_save, R.id.account_cancel, R.id.account_check, R.id.account_replace, R.id.account_remove)
    private var saved = false
    private var editing = false
    private var busy = false
    private var disposed = false
    private var visible = false
    private var message: String? = null

    init {
        for (field in listOf(key, secret)) {
            field.imeOptions = field.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }
        activity.findViewById<Button>(R.id.account_save).setOnClickListener { save() }
        activity.findViewById<Button>(R.id.account_cancel).setOnClickListener { clearFields(); editing = false; render() }
        activity.findViewById<Button>(R.id.account_replace).setOnClickListener { editing = true; clearFields(); render() }
        activity.findViewById<Button>(R.id.account_check).setOnClickListener { verify() }
        activity.findViewById<Button>(R.id.account_remove).setOnClickListener {
            if (!UiAccess.allowed(activity)) return@setOnClickListener
            val session = UiAccess.session()
            val dialog = AlertDialog.Builder(activity).setTitle("Hapus kredensial dari HP?")
                .setMessage("Untuk mencabut key di Binance, buka API Management di akun Binance.")
                .setNegativeButton("Batal", null).setPositiveButton("Hapus dari HP") { _, _ ->
                    if (UiAccess.allowed(activity, session)) remove()
                }.show()
            UiAccess.protect(activity, dialog)
        }
    }

    fun show(active: Boolean) {
        if (visible == active) return
        visible = active
        if (active) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            saved = vault.exists()
            render()
        } else {
            clearFields()
            editing = false
            if (activity !is com.mefuturesaibot.app.security.SecureActivity) {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    private fun render() {
        val form = editing || !saved
        activity.findViewById<View>(R.id.account_form).visibility = if (form) View.VISIBLE else View.GONE
        activity.findViewById<View>(R.id.account_actions).visibility = if (form) View.GONE else View.VISIBLE
        activity.findViewById<View>(R.id.account_cancel).visibility = if (editing && saved) View.VISIBLE else View.GONE
        status.text = message ?: if (saved) "Kredensial tersimpan · belum diuji pada sesi ini" else "Belum terhubung"
        if (info.text.isEmpty()) info.text = "Uji key tersimpan untuk membaca status akun Futures dan izin API."
        key.isEnabled = !busy
        secret.isEnabled = !busy
        buttons.forEach { activity.findViewById<Button>(it).isEnabled = !busy }
    }

    private fun clearFields() {
        if (key.hasFocus() || secret.hasFocus()) {
            val ime = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            ime.hideSoftInputFromWindow(key.windowToken, 0)
        }
        key.text.clear()
        secret.text.clear()
        key.error = null
        secret.error = null
    }

    private fun changeCredentials(action: () -> Unit) {
        synchronized(com.mefuturesaibot.app.demo.DemoRunner.entryLock) {
            if (com.mefuturesaibot.app.BuildConfig.MAINNET_ORDERS) {
                val live = com.mefuturesaibot.app.demo.FuturesVenue.REAL.prefs(activity)
                if (live.getBoolean("enabled",false) || live.contains("intent"))
                    throw AccountCheckError("Nonaktifkan trading dan tunggu konfirmasi posisi ditutup sebelum mengganti key.")
                check(live.edit().remove("consentKey").commit())
            }
            action()
        }
    }

    private fun save() {
        val api = key.text.toString().trim()
        val signing = secret.text.toString().trim()
        if (!valid(api)) { key.error = "Masukkan API Key HMAC tanpa spasi."; key.requestFocus(); return }
        if (!valid(signing)) { secret.error = "Masukkan API Secret HMAC tanpa spasi."; secret.requestFocus(); return }
        run("Menyimpan di perangkat…", "Gagal menyimpan secara terenkripsi. Periksa perangkat lalu coba lagi.",
            { changeCredentials { vault.save(BinanceCredentials(api, signing)) } }) {
            clearFields()
            editing = false
            message = "Kredensial tersimpan · siap diuji"
            info.text = ""
        }
    }

    private fun verify() {
        info.text = ""
        run("Menghubungi Binance…", "Gagal menguji key. Periksa jaringan atau ganti kredensial tersimpan.",
            { checker.verify(vault.read()) }) { access ->
            val checked = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
            message = "Terverifikasi oleh Binance · $checked"
            fun permission(value: Boolean?) = when (value) { true -> "Aktif"; false -> "Nonaktif"; null -> "Belum terverifikasi" }
            info.text = listOf(
                "Trading pada akun Futures   ${permission(access.canTrade)}",
                "Izin Futures pada API key   ${permission(access.futuresKey)}",
                "Izin penarikan pada API key   ${permission(access.withdrawals)}",
            ).joinToString("\n") + if (access.withdrawals == true) "\nMatikan izin penarikan untuk key bot di Binance." else ""
        }
    }

    private fun remove() {
        run("Menghapus dari perangkat…", "Penghapusan gagal. Coba lagi.", { changeCredentials { vault.remove() } }) {
            editing = false
            clearFields()
            message = null
            info.text = ""
        }
    }

    private fun valid(value: String) = value.isNotEmpty() && value.length <= 512 && value.all { it.code in 33..126 }

    private fun <T> run(progress: String, error: String, task: () -> T, done: (T) -> Unit) {
        if (busy || disposed) return
        busy = true
        message = progress
        render()
        worker.execute {
            val result = try { Result.success(task()) } catch (e: Exception) { Result.failure(e) }
            main.post {
                if (disposed) return@post
                busy = false
                saved = vault.exists()
                credentialsChanged()
                result.fold(done) { e -> message = (e as? AccountCheckError)?.explanation ?: error }
                render()
            }
        }
    }

    fun close() {
        disposed = true
        clearFields()
        checker.close()
        worker.shutdown()
    }
}
