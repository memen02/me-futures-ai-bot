package com.mefuturesaibot.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        // Jangan hidupkan bot jika Auto Start mati atau pengguna pernah menekan Stop.
        if (!BotPreferences.isAutoStartEnabled(context)) return
        val venue = com.mefuturesaibot.app.demo.FuturesVenue.selected(context)
        if (!BotPreferences.shouldRun(context) && !venue.prefs(context).contains("intent")) {
            if (com.mefuturesaibot.app.scanner.DeskStore(context).read().second.position != null)
                context.startForegroundService(Intent(context, ScreeningService::class.java))
            return
        }

        val serviceIntent = Intent(context, BotForegroundService::class.java)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("MEFuturesBoot", "Gagal memulai service setelah reboot", e)
        }
    }
}
