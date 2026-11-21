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
    private val vault = CredentialVault(activity, true)
    private val prefs = activity.getSharedPreferences(DemoRunner.PREFS, Context.MODE_PRIVATE)
    private val toggle = activity.findViewById<Switch>(R.id.demo_enabled)
    private val label = activity.findViewById<TextView>(R.id.demo_status)
    private val keys = activity.findViewById<Button>(R.id.demo_keys)
    private var rendering = false
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> render() }

    init {
        keys.setOnClickListener { edit() }
        toggle.setOnCheckedChangeListener { _, enabled ->
            if (!rendering) {
                synchronized(DemoRunner.entryLock) { prefs.edit().putBoolean("enabled", enabled).putString("status",
                    if (enabled) "Menghubungi Binance Demo…" else "Demo nonaktif · memeriksa sisa posisi").apply() }
                if (enabled) start()
                render()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        render()
    }

    private fun render() {
        rendering = true
        val enabled = prefs.getBoolean("enabled",false)
        toggle.isChecked = enabled
        toggle.isEnabled = vault.exists()
        keys.isEnabled = !enabled && !prefs.contains("intent")
        label.text = prefs.getString("status", if (vault.exists()) "Key demo tersimpan" else "Masukkan key Binance Demo untuk mulai")
        rendering = false
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
                runCatching { vault.remove() }.onSuccess { prefs.edit().clear().apply() }
                render()
            }.create()
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.setOnDismissListener { key.text.clear(); secret.text.clear() }
        dialog.show()
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val k = key.text.toString().trim()
            val s = secret.text.toString().trim()
            if (listOf(k,s).any { it.isEmpty() || it.length > 512 || !it.all { ch -> ch.code in 33..126 } }) {
                secret.error = "Isi pasangan key Demo tanpa spasi"
            } else {
                runCatching { vault.save(BinanceCredentials(k,s)) }.onSuccess {
                    prefs.edit().clear().apply()
                    dialog.dismiss()
                    render()
                }.onFailure { secret.error = "Penyimpanan terenkripsi gagal" }
            }
        }
    }

    fun close() { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
}
