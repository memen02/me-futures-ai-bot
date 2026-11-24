package com.mefuturesaibot.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        // Jangan hidupkan bot jika Auto Start mati atau pengguna pernah menekan Stop.
        if (!BotPreferences.isAutoStartEnabled(context)) return
        try {
            val venue = com.mefuturesaibot.app.demo.FuturesVenue.selected(context)
            val service = when {
                BotPreferences.shouldRun(context) || venue.prefs(context).contains("intent") -> BotForegroundService::class.java
                com.mefuturesaibot.app.scanner.DeskStore(context).read().second.position != null -> ScreeningService::class.java
                else -> return
            }
            context.startForegroundService(Intent(context,service))
        } catch (_: RuntimeException) {
            Log.w("MEFuturesBoot", "Android menunda pemantauan setelah reboot")
        }
    }
}
