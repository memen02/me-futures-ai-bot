package com.mefuturesaibot.app.auth

import android.content.Context
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.mefuturesaibot.app.maintenance.MeProjectApplication
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal interface DevicePermission {
    fun allowed(): Boolean
}

internal fun Context.hasDeviceAccess() =
    (applicationContext as? MeProjectApplication)?.devicePermission?.allowed() == true

internal object DeviceRegistry {
    const val releaseId = 461443416L
    const val url = "https://api.github.com/repos/memen02/me-futures-ai-bot/releases/461443416"
    private val codePattern = Regex("[0-9a-f]{64}")

    fun permits(json: String, code: String): Boolean {
        if (!codePattern.matches(code)) return false
        val release = JSONObject(json)
        if (release.getLong("id") != releaseId || release.getBoolean("draft") ||
            release.getString("tag_name") != "izin-perangkat-v1" ||
            release.getJSONObject("author").getLong("id") != 219340650L) return false
        val lines = release.getString("body").lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        require(lines.firstOrNull() == "ME-PROJECT-ACCESS-V1" && lines.drop(1).all(codePattern::matches))
        return code in lines.drop(1)
    }
}

private fun installationCode(): String {
    val alias = "me_project_device_approval_v1"
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    val key = store.getCertificate(alias)?.publicKey ?: KeyPairGenerator
        .getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").run {
            initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256).build())
            generateKeyPair().public
        }
    return MessageDigest.getInstance("SHA-256").digest(key.encoded).joinToString("") { "%02x".format(it) }
}

internal class RegistryClient {
    private val http = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).callTimeout(10, TimeUnit.SECONDS).build()

    fun permits(code: String): Boolean {
        val req = Request.Builder().url(DeviceRegistry.url)
            .header("Cache-Control", "no-cache").header("Accept","application/vnd.github+json").build()
        return http.newCall(req).execute().use { result ->
            check(result.isSuccessful)
            val source = result.body.source()
            check(!source.request(256 * 1024L + 1))
            DeviceRegistry.permits(source.readUtf8(), code)
        }
    }
}

/** Recheck on use, with a monotonic in-memory lease. Restart always starts locked. */
internal class DeviceAccess(
    private val codeForInstall: () -> String = ::installationCode,
    private val lookup: (String) -> Boolean = RegistryClient()::permits,
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
    private val worker: java.util.concurrent.Executor = Executors.newSingleThreadExecutor()
) : DevicePermission {
    @Volatile var code: String? = null
        private set
    @Volatile var message = "Memeriksa izin perangkat…"
        private set
    private var until = 0L
    private var nextCheck = 0L
    private var busy = false

    @Synchronized override fun allowed(): Boolean {
        refresh()
        return elapsed() < until
    }

    @Synchronized fun refresh(manual: Boolean = false) {
        val now = elapsed()
        if (busy || now < nextCheck && (!manual || now < manualAfter)) return
        busy = true
        manualAfter = now + 15_000L
        worker.execute {
            val result = runCatching {
                val id = code ?: codeForInstall().also { code = it }
                lookup(id)
            }
            synchronized(this) {
                val end = elapsed()
                val granted = result.getOrNull() == true
                until = if (granted) now + 300_000L else 0L
                nextCheck = end + if (granted) 240_000L else 60_000L
                message = when {
                    granted -> "Izin pemilik aktif"
                    result.isFailure -> "Izin belum dapat diverifikasi. Periksa koneksi lalu coba lagi."
                    else -> "Menunggu persetujuan pemilik"
                }
                busy = false
            }
        }
    }
    private var manualAfter = 0L
}
