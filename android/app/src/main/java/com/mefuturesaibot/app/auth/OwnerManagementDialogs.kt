package com.mefuturesaibot.app.auth

import android.app.AlertDialog
import android.app.Activity
import android.widget.EditText
import android.text.InputFilter

internal class OwnerManagementDialogs(
    private val activity: Activity,
    private val coordinator: OwnerPanelCoordinator
) {
    fun license(row: OwnerDirectoryRow) {
        if (activity.isFinishing || activity.isDestroyed) return
        val choices = arrayOf("FAMILY — gratis", "MONTHLY — 30 hari", "Cabut lisensi")
        AlertDialog.Builder(activity).setTitle("Lisensi: " + row.username)
            .setItems(choices) { _, index ->
                confirm(choices[index] + " untuk " + row.username + "?") {
                    when (index) {
                        0 -> coordinator.grantLicense(row, "FAMILY", null)
                        1 -> coordinator.grantLicense(row, "MONTHLY",
                            System.currentTimeMillis() / 1000L + 30L * 86400L)
                        2 -> coordinator.revokeLicense(row)
                    }
                }
            }.setNegativeButton("Batal", null).show()
    }

    fun device(row: OwnerDirectoryRow) {
        if (activity.isFinishing || activity.isDestroyed) return
        val input = EditText(activity).apply {
            hint = "Kode perangkat (64 karakter)"
            isSingleLine = true
            filters = arrayOf(InputFilter.LengthFilter(64))
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Perangkat: " + row.username)
            .setMessage("Penggantian perangkat akan mencabut sesi lama.")
            .setView(input).setNegativeButton("Batal", null)
            .setPositiveButton("Lanjut", null).create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val digest = input.text.toString().trim()
            if (!digest.matches(Regex("[0-9a-f]{64}")) || digest == row.deviceDigest) {
                input.error = "Kode perangkat baru tidak valid"
            } else {
                dialog.dismiss()
                confirm("Daftarkan perangkat baru untuk " + row.username + "?") {
                    coordinator.replaceDevice(row, digest)
                }
            }
        }
    }

    private fun confirm(message: String, action: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) return
        AlertDialog.Builder(activity).setMessage(message)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Konfirmasi") { _, _ -> action() }.show()
    }
}
