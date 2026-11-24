package com.mefuturesaibot.app.security

import android.app.Activity
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal

internal object DeviceAuthentication {

    const val REQUEST_CREDENTIAL = 8201

    fun authenticate(
        activity: Activity,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
        credentialOnly: Boolean = false
    ): CancellationSignal? {

        val manager =
            activity.getSystemService(KeyguardManager::class.java)

        if (manager == null || !manager.isDeviceSecure) {
            onFailure("Aktifkan keamanan layar kunci Android terlebih dahulu.")
            return null
        }

        if (Build.VERSION.SDK_INT >= 30 && !credentialOnly) {
            val cancellation = CancellationSignal()

            try {
                val prompt = BiometricPrompt.Builder(activity)
                    .setTitle("ME Project")
                    .setSubtitle("Verifikasi identitas Anda")
                    .setAllowedAuthenticators(
                        android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
                    .build()

                prompt.authenticate(
                    cancellation,
                    activity.mainExecutor,
                    object : BiometricPrompt.AuthenticationCallback() {

                        override fun onAuthenticationSucceeded(
                            result: BiometricPrompt.AuthenticationResult
                        ) {
                            if (!activity.isFinishing &&
                                !activity.isDestroyed &&
                                !cancellation.isCanceled
                            ) {
                                onSuccess()
                            }
                        }

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence
                        ) {
                            onFailure(errString.toString())
                        }
                    }
                )
                return cancellation

            } catch (_: RuntimeException) {
                onFailure("Autentikasi tidak dapat dimulai.")
                return null
            }
        }

        @Suppress("DEPRECATION")
        val intent = manager.createConfirmDeviceCredentialIntent(
            "ME Project",
            "Verifikasi keamanan perangkat"
        )

        if (intent == null) {
            onFailure("Autentikasi perangkat tidak tersedia.")
            return null
        }

        try {
            @Suppress("DEPRECATION")
            activity.startActivityForResult(
                intent, REQUEST_CREDENTIAL
            )
        } catch (_: RuntimeException) {
            onFailure("Autentikasi perangkat gagal dimulai.")
        }

        return null
    }
}
