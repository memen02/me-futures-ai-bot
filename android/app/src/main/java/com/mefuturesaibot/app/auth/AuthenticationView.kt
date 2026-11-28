package com.mefuturesaibot.app.auth

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.InputType
import android.text.InputFilter
import android.widget.*
import com.mefuturesaibot.app.security.SecureActivity

internal class AuthenticationView(
    private val activity: Activity,
    private val runtime: AuthenticationRuntime,
    private val onAuthenticated: () -> Unit
) : LinearLayout(activity), AutoCloseable {
    private val worker = OwnerRequestWorker()
    @Volatile private var closed = false
    @Volatile private var pendingLogin: Long? = null
    private val username = EditText(activity).apply {
        hint = "Username"
        isSingleLine = true
        filters = arrayOf(InputFilter.LengthFilter(32))
    }
    private fun passwordField(label: String) = EditText(activity).apply {
        hint = label
        isSaveEnabled = false
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        filters = arrayOf(InputFilter.LengthFilter(128))
    }
    private val password = passwordField("Password")
    private val repeated = passwordField("Ulangi password untuk registrasi")
    private val status = TextView(activity)
    init {
        orientation = VERTICAL
        addView(username)
        addView(password)
        addView(repeated)
        button("Login USER") { login(UserRole.USER) }
        button("Login OWNER") { login(UserRole.SUPER_ADMIN) }
        button("Registrasi") {
            val name = username.text.toString()
            val p = take(password)
            val r = take(repeated)
            if (!worker.submit({ runtime.register(name,p,r) }) {
                status.text = it.getOrNull()?.name ?: "Registrasi gagal"
            }) {
                p.fill('\u0000')
                r.fill('\u0000')
                status.text = "Permintaan sedang diproses"
            }
        }
        button("Salin kode perangkat") {
            if (!worker.submit({ runtime.deviceCode() }) { result ->
                val code = result.getOrNull()
                if (code == null) status.text = "Kode perangkat tidak tersedia"
                else {
                    val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Kode perangkat",code))
                    status.text = "Kode perangkat disalin"
                }
            }) status.text = "Permintaan sedang diproses"
        }
        addView(status)
    }

    private fun login(role: UserRole) {
        val name = username.text.toString()
        val p = take(password)
        repeated.text.clear()
        val expected = runtime.sessions.revision()
        if (pendingLogin != null) { p.fill('\u0000'); return }
        pendingLogin = expected
        if (!worker.submit({ runtime.login(role,name,p,expected) { !closed && pendingLogin == expected } }) { result ->
            if (pendingLogin != expected) return@submit
            if (closed || activity.isFinishing || activity.isDestroyed ||
                (activity as? SecureActivity)?.canUseProtectedUi() != true) {
                cancelPendingLogin()
                return@submit
            }
            pendingLogin = null
            if (result.getOrNull() == true) {
                if ((activity as? SecureActivity)?.canUseProtectedUi() == true) onAuthenticated()
                else status.text = "Login selesai; verifikasi ulang diperlukan"
            }
            else status.text = "Login gagal atau akun belum disetujui"
        }) {
            pendingLogin = null
            p.fill('\u0000')
            status.text = "Permintaan sedang diproses"
        }
    }

    fun cancelPendingLogin() {
        var token: String? = null
        runtime.sessions.withSessionLock {
            val expected = pendingLogin
            pendingLogin = null
            if (expected != null && runtime.sessions.revision() == expected + 2L) {
                token = runtime.sessions.clear()
            }
        }
        token?.let { value -> Thread { runtime.revokeSession(value) }.start() }
    }

    private fun take(field: EditText): CharArray {
        val value = CharArray(field.text.length) { field.text[it] }
        field.text.clear()
        return value
    }

    private fun button(label: String, action: () -> Unit) {
        addView(Button(activity).apply {
            text = label
            setOnClickListener {
                if (!closed && (activity as? SecureActivity)?.canUseProtectedUi() == true) action()
            }
        })
    }

    override fun close() {
        runtime.sessions.withSessionLock { closed = true }
        cancelPendingLogin()
        worker.close()
        password.text.clear()
        repeated.text.clear()
    }
}
