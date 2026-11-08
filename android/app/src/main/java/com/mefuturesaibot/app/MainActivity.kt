package com.mefuturesaibot.app

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * FASE 10 -- Fondasi. Ini sengaja minimal: tujuannya cuma membuktikan pipeline
 * build (Gradle + GitHub Actions) benar-benar jalan, sebelum logic trading
 * sungguhan ditambahkan secara bertahap. Layout dibuat programatik (bukan file
 * XML terpisah) supaya lebih sedikit bagian yang bisa salah di percobaan
 * compile pertama.
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val titleView = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#0F172A"))
            setPadding(48, 160, 48, 24)
        }

        val statusView = TextView(this).apply {
            text = getString(R.string.status_foundation)
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#475569"))
            setPadding(48, 0, 48, 48)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            addView(titleView)
            addView(statusView)
        }

        setContentView(root)
    }
}
