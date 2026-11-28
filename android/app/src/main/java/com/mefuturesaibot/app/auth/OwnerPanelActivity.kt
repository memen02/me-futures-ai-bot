package com.mefuturesaibot.app.auth

import android.os.Bundle
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
        setContentView(host!!.view)
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
