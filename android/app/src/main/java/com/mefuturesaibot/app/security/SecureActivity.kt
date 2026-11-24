package com.mefuturesaibot.app.security

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CancellationSignal
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

open class SecureActivity : Activity() {

    private var screenReceiverRegistered = false

    private val screenReceiver =
        object : android.content.BroadcastReceiver() {
            override fun onReceive(
                context: android.content.Context,
                intent: Intent
            ) {
                if (intent.action != Intent.ACTION_SCREEN_OFF) return

                AppLock.lock()
                cancelAuthentication()
                autoPromptAttempted = false
                dismissProtectedDialogs()

                if (AppLock.isEnabled(this@SecureActivity)) {
                    showLock()
                }
            }
        }

    private var shield: FrameLayout? = null
    private var cancellation: CancellationSignal? = null
    private var authenticating = false
    private val protectedDialogs =
        mutableSetOf<android.app.Dialog>()

    internal fun registerProtectedDialog(
        dialog: android.app.Dialog
    ) {
        protectedDialogs.removeAll { !it.isShowing }
        if (!canUseProtectedUi()) {
            dialog.dismiss()
            return
        }

        dialog.window?.addFlags(
            WindowManager.LayoutParams.FLAG_SECURE
        )

        protectedDialogs.add(dialog)
    }

    private fun dismissProtectedDialogs() {
        val dialogs = protectedDialogs.toList()
        protectedDialogs.clear()
        dialogs.forEach { it.dismiss() }
    }

    private var resumed = false
    private var unlockStatus: TextView? = null
    private var autoPromptAttempted = false
    private var countedAsStarted = false

    companion object {
        private var startedActivities = 0
    }

    override fun onStart() {
        super.onStart()

        if (!countedAsStarted) {
            countedAsStarted = true
            startedActivities++
        }

        if (AppLock.requiresUnlock(this)) {
            showLock()
        }
    }
    private var generation = 0
    private var legacyPending = false

    internal fun canUseProtectedUi(): Boolean =
        resumed &&
            !isFinishing &&
            !isDestroyed &&
            !AppLock.requiresUnlock(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val filter = android.content.IntentFilter(
            Intent.ACTION_SCREEN_OFF
        )

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                screenReceiver,
                filter,
                android.content.Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenReceiver, filter)
        }

        screenReceiverRegistered = true

        window.addFlags(
            WindowManager.LayoutParams.FLAG_SECURE
        )
    }

    override fun onResume() {
        super.onResume()
        resumed = true

        if (AppLock.requiresUnlock(this)) {
            showLock()
            launchAutomaticUnlock()
        } else {
            hideLock()
        }
    }

    override fun onPause() {
        resumed = false
        AppLock.invalidateUiSession()
        dismissProtectedDialogs()

        if (AppLock.isEnabled(this)) {
            showLock()
        }

        if (!authenticating) {
            cancelAuthentication()
        }

        super.onPause()
    }

    override fun onStop() {
        if (countedAsStarted) {
            countedAsStarted = false
            startedActivities =
                (startedActivities - 1).coerceAtLeast(0)
        }

        if (startedActivities == 0) {
            AppLock.lock()
        }

        if (AppLock.isEnabled(this)) {
            showLock()
        }

        if (!legacyPending) {
            cancelAuthentication()
            autoPromptAttempted = false
        }

        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus && resumed &&
            AppLock.requiresUnlock(this)
        ) {
            showLock()
            launchAutomaticUnlock()
        }
    }

    private fun launchAutomaticUnlock() {
        if (!resumed || authenticating ||
            autoPromptAttempted ||
            !hasWindowFocus() ||
            !AppLock.requiresUnlock(this)
        ) return

        val status = unlockStatus ?: return
        autoPromptAttempted = true
        requestUnlock(status)
    }

    private fun showLock() {
        if (shield != null || isFinishing) return

        val root = window.decorView as? FrameLayout
            ?: return

        val cover = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(12, 14, 18))
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            elevation = 10000f
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
        }

        panel.addView(TextView(this).apply {
            text = "ME PROJECT"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        })

        panel.addView(TextView(this).apply {
            text = "Aplikasi terkunci"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, dp(24), 0, dp(12))
        })

        val status = TextView(this).apply {
            text = "Verifikasi identitas untuk melanjutkan"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
        }

        panel.addView(status)
        unlockStatus = status

        panel.addView(Button(this).apply {
            text = "Gunakan PIN atau pola"
            isAllCaps = false
            setOnClickListener {
                requestUnlock(status, credentialOnly = true)
            }
        })

        cover.addView(
            panel,
            FrameLayout.LayoutParams(
                -1, -2, Gravity.CENTER
            )
        )

        root.addView(
            cover,
            FrameLayout.LayoutParams(-1, -1)
        )

        shield = cover

        findViewById<View>(android.R.id.content)?.apply {
            visibility = View.INVISIBLE
            importantForAccessibility =
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }

        val keyboard = getSystemService(
            android.view.inputmethod.InputMethodManager::class.java
        )

        keyboard?.hideSoftInputFromWindow(
            root.windowToken, 0
        )

        cover.requestFocus()
    }

    internal fun configureAppLock(
        enable: Boolean,
        credentialOnly: Boolean = false
    ) {
        if (!canUseProtectedUi() || authenticating) return

        if (!AppLock.deviceSecure(this)) {
            android.widget.Toast.makeText(
                this,
                "Aktifkan PIN, pola atau password Android terlebih dahulu.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return
        }

        authenticating = true
        val request = ++generation
        pendingLockSetting = enable
        legacyPending = credentialOnly ||
            android.os.Build.VERSION.SDK_INT < 30

        cancellation = DeviceAuthentication.authenticate(
            this,
            credentialOnly = credentialOnly,
            onSuccess = {
                if (request == generation &&
                    !isFinishing && !isDestroyed) {
                    completeLockSetting()
                }
            },
            onFailure = { message ->
                if (request == generation) {
                    authenticating = false
                    legacyPending = false
                    pendingLockSetting = null
                    cancellation = null
                    android.widget.Toast.makeText(
                        this, message,
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    private var pendingLockSetting: Boolean? = null

    private fun completeLockSetting() {
        val enable = pendingLockSetting ?: return

        pendingLockSetting = null
        authenticating = false
        legacyPending = false
        cancellation = null

        AppLock.markAuthenticated()

        val saved = if (enable) {
            AppLock.enableAfterAuthentication(this)
        } else {
            AppLock.disableAfterAuthentication(this)
        }

        android.widget.Toast.makeText(
            this,
            if (!saved) "Pengaturan keamanan gagal disimpan"
            else if (enable) "App Lock diaktifkan"
            else "App Lock dinonaktifkan",
            android.widget.Toast.LENGTH_LONG
        ).show()

        if (resumed) {
            if (AppLock.requiresUnlock(this)) showLock()
            else hideLock()
        }

        onSecuritySettingsChanged()
    }

    protected open fun onSecuritySettingsChanged() {}

    private fun hideLock() {
        val cover = shield ?: return
        (cover.parent as? FrameLayout)?.removeView(cover)
        shield = null
        unlockStatus = null
        autoPromptAttempted = false

        findViewById<View>(android.R.id.content)?.apply {
            visibility = View.VISIBLE
            importantForAccessibility =
                View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
    }

    private fun requestUnlock(
        status: TextView,
        credentialOnly: Boolean = false
    ) {
        if (authenticating || !resumed) return

        authenticating = true
        val request = ++generation

        legacyPending = credentialOnly ||
            android.os.Build.VERSION.SDK_INT < 30

        cancellation = DeviceAuthentication.authenticate(
            this,
            credentialOnly = credentialOnly,
            onSuccess = {
                if (request == generation &&
                    !isFinishing &&
                    !isDestroyed &&
                    AppLock.deviceSecure(this)) {
                    authenticating = false
                    legacyPending = false
                    cancellation = null
                    AppLock.markAuthenticated()
                    if (resumed) hideLock()
                }
            },
            onFailure = { message ->
                if (request == generation) {
                    authenticating = false
                    legacyPending = false
                    cancellation = null
                    status.text = message

                    if (resumed && AppLock.requiresUnlock(this)) {
                        moveTaskToBack(true)
                    }
                }
            }
        )
    }

    private fun cancelAuthentication() {
        pendingLockSetting = null
        generation++
        authenticating = false
        legacyPending = false
        cancellation?.cancel()
        cancellation = null
    }

    @Deprecated("Native activity result")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(
            requestCode, resultCode, data
        )

        if (requestCode ==
            DeviceAuthentication.REQUEST_CREDENTIAL &&
            legacyPending
        ) {
            legacyPending = false
            authenticating = false

            if (resultCode == RESULT_OK &&
                AppLock.deviceSecure(this)
            ) {
                if (pendingLockSetting != null) {
                    completeLockSetting()
                } else {
                    AppLock.markAuthenticated()
                }
            } else {
                pendingLockSetting = null
                AppLock.lock()

                if (AppLock.requiresUnlock(this)) {
                    moveTaskToBack(true)
                }
            }
        }
    }

    @Deprecated("Native back navigation")
    override fun onBackPressed() {
        if (AppLock.requiresUnlock(this)) {
            moveTaskToBack(true)
            return
        }

        super.onBackPressed()
    }

    override fun onDestroy() {
        if (screenReceiverRegistered) {
            unregisterReceiver(screenReceiver)
            screenReceiverRegistered = false
        }

        dismissProtectedDialogs()
        cancelAuthentication()
        hideLock()
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f)
            .toInt()
}
