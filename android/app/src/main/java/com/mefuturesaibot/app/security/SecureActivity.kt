package com.mefuturesaibot.app.security

import android.app.Activity
import com.mefuturesaibot.app.auth.ApplicationAuthentication
import com.mefuturesaibot.app.StartupActivity
import android.os.Handler
import android.os.Looper
import com.mefuturesaibot.app.BuildConfig
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CancellationSignal
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

open class SecureActivity : Activity() {

    protected open val requiresBackendAccount: Boolean = false
    protected open val requiresOwnerAccount: Boolean = false
    private val accountHandler = Handler(Looper.getMainLooper())
    private val accountCheck = object : Runnable {
        override fun run() {
            if (!resumed || isFinishing || isDestroyed) return
            if (!accountAllowed()) {
                dismissProtectedDialogs()
                showLock()
                onProtectedUiRevoked()
                startActivity(Intent(this@SecureActivity, StartupActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            } else {
                accountHandler.postDelayed(this, 1000L)
            }
        }
    }

    private fun accountAllowed(): Boolean =
        if (requiresOwnerAccount) ApplicationAuthentication.allowsOwnerScreen()
        else !requiresBackendAccount || ApplicationAuthentication.allowsDashboard()

    private var screenReceiverRegistered = false

    private val screenReceiver =
        object : android.content.BroadcastReceiver() {
            override fun onReceive(
                context: android.content.Context,
                intent: Intent
            ) {
                if (intent.action != Intent.ACTION_SCREEN_OFF) return

                AppLock.lock()
                onProtectedUiRevoked()
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

    internal fun canUseProtectedUi(): Boolean =
        resumed &&
            !isFinishing &&
            !isDestroyed &&
            !AppLock.requiresUnlock(this) && accountAllowed()

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

        // DEV permits dashboard screenshots for UI testing.
        // REAL and other variants retain screen capture protection.
        if (BuildConfig.BUILD_TYPE != "dev" || BuildConfig.MAINNET_ORDERS) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (requiresBackendAccount) {
            accountHandler.removeCallbacks(accountCheck)
            accountCheck.run()
            if (!accountAllowed() || isFinishing) return
        }

        if (AppLock.requiresUnlock(this)) {
            showLock()
            launchAutomaticUnlock()
        } else {
            hideLock()
        }
    }

    override fun onPause() {
        resumed = false
        accountHandler.removeCallbacks(accountCheck)
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

        cancelAuthentication()
        autoPromptAttempted = false

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
            text = "Lihat layar atau gunakan biometrik yang didukung perangkat"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
        }

        panel.addView(status)
        unlockStatus = status

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
        enable: Boolean
    ) {
        if (!canUseProtectedUi() || authenticating) return

        if (!DeviceAuthentication.available(this)) {
            android.widget.Toast.makeText(
                this,
                "Aktifkan biometrik atau kunci layar pada pengaturan Android terlebih dahulu.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return
        }

        authenticating = true
        val request = ++generation
        pendingLockSetting = enable


        cancellation = DeviceAuthentication.authenticate(
            this,
            onSuccess = {
                if (request == generation &&
                    !isFinishing && !isDestroyed) {
                    completeLockSetting()
                }
            },
            onFailure = { message ->
                if (request == generation) {
                    authenticating = false
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

    protected open fun onProtectedUiRevoked() {}

    protected open fun onProtectedUiUnlocked() {}

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
        status: TextView
    ) {
        if (authenticating || !resumed) return

        authenticating = true
        val request = ++generation



        cancellation = DeviceAuthentication.authenticate(
            this,
            onSuccess = {
                if (request == generation &&
                    !isFinishing &&
                    !isDestroyed &&
                    AppLock.deviceSecure(this)) {
                    authenticating = false
                    cancellation = null
                    AppLock.markAuthenticated()
                    if (resumed) {
                        hideLock()
                        if (canUseProtectedUi()) onProtectedUiUnlocked()
                    }
                }
            },
            onFailure = { message ->
                if (request == generation) {
                    authenticating = false
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
        cancellation?.cancel()
        cancellation = null
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
        accountHandler.removeCallbacks(accountCheck)
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
