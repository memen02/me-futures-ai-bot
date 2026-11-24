package com.mefuturesaibot.app.security

import android.app.Activity

internal object UiAccess {

    fun allowed(activity: Activity): Boolean {
        if (activity.isFinishing || activity.isDestroyed) {
            return false
        }

        return if (activity is SecureActivity) {
            activity.canUseProtectedUi()
        } else {
            !AppLock.isEnabled(activity)
        }
    }

    fun <T : android.app.Dialog> protect(
        activity: Activity,
        dialog: T
    ): T {
        if (activity is SecureActivity) {
            activity.registerProtectedDialog(dialog)
        } else if (!allowed(activity)) {
            dialog.dismiss()
        }
        return dialog
    }

    fun session(): Long = AppLock.sessionId()

    fun allowed(activity: Activity, session: Long): Boolean =
        allowed(activity) && session == AppLock.sessionId()
}
