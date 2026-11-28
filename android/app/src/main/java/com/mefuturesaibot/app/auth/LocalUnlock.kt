package com.mefuturesaibot.app.auth

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal

// Local unlock only; never grants or renews a backend session.
internal class LocalUnlock(private val activity: Activity) : AutoCloseable {
    private var pending: CancellationSignal? = null
    private var generation = 0L

    fun authenticate(onResult: (Boolean) -> Unit) {
        close()
        val current = generation
        val keyguard = activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!keyguard.isDeviceSecure || activity.isFinishing || activity.isDestroyed) {
            onResult(false)
            return
        }
        if (Build.VERSION.SDK_INT < 30) {
            onResult(false)
            return
        }
        val signal = CancellationSignal()
        pending = signal
        fun finish(success: Boolean) {
            if (generation != current || pending !== signal) return
            pending = null
            onResult(success && !activity.isFinishing && !activity.isDestroyed)
        }
        try {
            BiometricPrompt.Builder(activity)
                .setTitle("Buka ME Project")
                .setAllowedAuthenticators(
                    android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build().authenticate(signal, activity.mainExecutor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            finish(true)
                        }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            finish(false)
                        }
                    })
        } catch (_: Exception) {
            finish(false)
        }
    }

    override fun close() {
        generation++
        val old = pending
        pending = null
        old?.cancel()
    }
}
