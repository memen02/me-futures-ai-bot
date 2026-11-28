package com.mefuturesaibot.app.auth

import android.os.Bundle
import android.content.Intent
import android.widget.Button
import android.widget.LinearLayout
import com.mefuturesaibot.app.MainActivity
import com.mefuturesaibot.app.StartupActivity
import android.widget.Toast
import com.mefuturesaibot.app.security.SecureActivity

internal class OwnerPanelActivity : SecureActivity() {
    override val requiresBackendAccount: Boolean = true
    private var host: OwnerPanelHost? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val runtime = ApplicationAuthentication.current()
        if (runtime == null || !ApplicationAuthentication.allowsOwnerScreen()) {
            finish()
            return
        }
        host = runtime.ownerPanel(this) { message ->
            if (!isFinishing && !isDestroyed)
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(Button(this@OwnerPanelActivity).apply {
                text = "Dashboard trading"
                setOnClickListener {
                    if (canUseProtectedUi() && ApplicationAuthentication.allowsOwnerScreen()) {
                        startActivity(Intent(this@OwnerPanelActivity, MainActivity::class.java))
                    }
                }
            })
            addView(Button(this@OwnerPanelActivity).apply {
                text = "Logout"
                setOnClickListener {
                    if (!canUseProtectedUi() || !ApplicationAuthentication.allowsOwnerScreen()) return@setOnClickListener
                    ApplicationAuthentication.lockLocalAccess()
                    // Clear locally immediately; remote revocation runs off the UI thread.
                    val token = runtime.sessions.clear()
                    if (token != null) Thread { runtime.revokeSession(token) }.start()
                    startActivity(Intent(this@OwnerPanelActivity, StartupActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    finish()
                }
            })
            addView(host!!.view, LinearLayout.LayoutParams(-1, 0, 1f))
        })
    }

    override fun onResume() {
        super.onResume()
        if (!ApplicationAuthentication.allowsOwnerScreen()) {
            finish()
            return
        }
        host?.refresh()
    }

    override fun onProtectedUiRevoked() {
        host?.close()
        host = null
        finish()
    }

    override fun onDestroy() {
        host?.close()
        host = null
        super.onDestroy()
    }
}
