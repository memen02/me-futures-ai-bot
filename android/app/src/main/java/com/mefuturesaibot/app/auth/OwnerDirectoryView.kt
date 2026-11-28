package com.mefuturesaibot.app.auth

import android.content.Context
import android.graphics.Color
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

internal class OwnerDirectoryView(context: Context) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        setBackgroundColor(Color.rgb(20, 24, 30))
    }

    fun render(page: OwnerDirectoryPage,
        review: (OwnerDirectoryRow, AccountStatus) -> Unit,
        next: (String) -> Unit) {
        removeAllViews()
        label("OWNER • Pengelolaan pengguna", Color.rgb(240, 185, 11))
        if (page.users.isEmpty()) label("Tidak ada pengguna")
        for (row in page.users) {
            label(row.username + " • " + row.status.name)
            label("Lisensi: " + (row.licenseKind ?: "Belum aktif") +
                " | Perangkat: " + if (row.deviceDigest == null) "Belum terdaftar" else "Terdaftar")
            for (desired in AccountStatus.values()) {
                if (!AccountApprovalPolicy.reviewTransition(row.status, desired)) continue
                val caption = when (desired) {
                    AccountStatus.APPROVED -> "Setujui"
                    AccountStatus.REJECTED -> "Tolak"
                    AccountStatus.SUSPENDED -> "Tangguhkan"
                    else -> continue
                }
                addView(Button(context).apply {
                    text = caption
                    setOnClickListener { review(row, desired) }
                })
            }
        }
        page.nextCursor?.let { cursor ->
            addView(Button(context).apply {
                text = "Halaman berikutnya"
                setOnClickListener { next(cursor) }
            })
        }
    }

    private fun label(value: String, color: Int = Color.WHITE) {
        addView(TextView(context).apply {
            text = value
            setTextColor(color)
            textSize = 16f
        })
    }
}
