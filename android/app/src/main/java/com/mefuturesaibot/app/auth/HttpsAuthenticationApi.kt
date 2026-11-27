package com.mefuturesaibot.app.auth

import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

// Integration adapter only. No production origin or UI activation yet.
// Server HTTP adapter must implement this exact contract before use.
internal class HttpsAuthenticationApi(origin: HttpUrl) : AuthenticationApi {
    private val security = AuthenticationTransportSecurity(origin)
    private val client = security.client()

    private fun post(route: String, payload: JSONObject, token: String? = null): JSONObject {
        val builder = Request.Builder().url(security.endpoint("v1","auth",route))
            .header("Accept","application/json")
            .header("Cache-Control","no-store")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
        if (token != null) {
            require(token.matches(Regex("[a-f0-9]{64}")))
            builder.header("Authorization","Bearer $token")
        }
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("Authentication request rejected")
            val body = response.body ?: throw java.io.IOException("Missing response")
            val source = body.source()
            if (source.request(16385L)) throw java.io.IOException("Authentication response too large")
            return JSONObject(source.readUtf8())
        }
    }

    override fun register(username: String, password: CharArray): RegistrationResult =
        try {
            RegistrationResult.valueOf(post("register",JSONObject()
                .put("username",username).put("password",String(password))).getString("result"))
        } catch (_: Exception) { RegistrationResult.UNAVAILABLE }

    override fun login(username: String, password: CharArray): AuthGrant? =
        try {
            val value = post("login",JSONObject().put("username",username).put("password",String(password)))
            val identity = AccountIdentity(value.getString("accountId"),value.getString("username"),
                UserRole.valueOf(value.getString("role")),AccountStatus.valueOf(value.getString("status")))
            val token = value.getString("accessToken")
            if (!identity.valid() || identity.username != username || !token.matches(Regex("[a-f0-9]{64}"))) null
            else AuthGrant(identity,token,value.getLong("lifetimeSeconds"))
        } catch (_: Exception) { null }

    override fun revoke(accessToken: String) {
        if (post("logout",JSONObject(),accessToken).getString("result") != "APPLIED")
            throw java.io.IOException("Session revocation not confirmed")
    }
}
