package com.mefuturesaibot.app.security

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.os.CancellationSignal

internal object DeviceAuthentication {

    @Suppress("DEPRECATION")
    fun available(context: Context): Boolean {
        if (!AppLock.deviceSecure(context)) return false

        return when {
            Build.VERSION.SDK_INT >= 30 ->
                context.getSystemService(BiometricManager::class.java)
                    ?.canAuthenticate(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    ) == BiometricManager.BIOMETRIC_SUCCESS

            Build.VERSION.SDK_INT >= 28 ->
                context.getSystemService(FingerprintManager::class.java)
                    ?.let {
                        it.isHardwareDetected && it.hasEnrolledFingerprints()
                    } == true

            else -> false
        }
    }

    fun authenticate(
        activity: Activity,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ): CancellationSignal? {

        if (Build.VERSION.SDK_INT < 28 || !available(activity)) {
            onFailure("Biometrik tidak tersedia. Periksa pengaturan biometrik dan kunci layar Android.")
            return null
        }

        val cancellation = CancellationSignal()
        var completed = false

        fun fail(message: String) {
            if (completed || cancellation.isCanceled) return
            completed = true
            onFailure(message)
        }

        try {
            val builder = BiometricPrompt.Builder(activity)
                .setTitle("ME Project")
                .setSubtitle("Verifikasi wajah, sidik jari atau kunci layar")

            if (Build.VERSION.SDK_INT < 30) builder.setNegativeButton(
                    "Batal",
                    activity.mainExecutor
                ) { _, _ ->
                    fail("Autentikasi dibatalkan")
                }

            if (Build.VERSION.SDK_INT >= 30) {
                builder.setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG
                )
            }

            builder.build().authenticate(
                cancellation,
                activity.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {

                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult
                    ) {
                        if (!completed &&
                            !activity.isFinishing &&
                            !activity.isDestroyed &&
                            !cancellation.isCanceled
                        ) {
                            completed = true
                            onSuccess()
                        }
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence
                    ) {
                        fail(errString.toString())
                    }
                }
            )

            return cancellation

        } catch (_: RuntimeException) {
            fail("Autentikasi tidak dapat dimulai.")
            return null
        }
    }
}
