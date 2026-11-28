package com.mefuturesaibot.app.auth

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.*
import com.mefuturesaibot.app.StartupActivity
import com.mefuturesaibot.app.maintenance.MeProjectApplication

class DeviceAccessActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val access get() = (application as MeProjectApplication).onlineDeviceAccess
    private lateinit var code: TextView
    private lateinit var status: TextView
    private lateinit var copy: Button
    private val tick = object : Runnable {
        override fun run() {
            if (access.allowed()) {
                startActivity(Intent(this@DeviceAccessActivity, StartupActivity::class.java))
                finish()
                return
            }
            status.text = access.message
            code.text = access.code ?: "…"
            copy.isEnabled = access.code != null
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val padding = (24 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        fun label(text: String, size: Float, color: String): TextView = TextView(this).also {
            it.text = text
            it.textSize = size
            it.setTextColor(Color.parseColor(color))
            it.setPadding(0, padding / 3, 0, padding / 3)
            layout.addView(it)
        }
        label("ME Project", 26f, "#F0B90B")
        status = label("Memeriksa izin perangkat…", 19f, "#EAECEF")
        label("Kirim kode ini kepada pemilik ME Project untuk meminta akses. Setelah disetujui, ketuk Periksa izin.", 15f, "#B7BDC6")
        code = label("…", 16f, "#EAECEF").apply { setTextIsSelectable(true) }
        copy = Button(this).apply {
            text = "Salin kode perangkat"
            isEnabled = false
            setOnClickListener {
                access.code?.let { id ->
                    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Kode ME Project", id))
                    Toast.makeText(this@DeviceAccessActivity, "Kode disalin", Toast.LENGTH_SHORT).show()
                }
            }
        }
        layout.addView(copy)
        layout.addView(Button(this).apply { text = "Periksa izin"; setOnClickListener { access.refresh(manual = true) } })
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0B0E11")); isFillViewport = true; addView(layout) })
    }
    override fun onStart() { super.onStart(); tick.run() }
    override fun onStop() { handler.removeCallbacks(tick); super.onStop() }
}
