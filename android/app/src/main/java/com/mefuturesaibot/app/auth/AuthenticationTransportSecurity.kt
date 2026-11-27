package com.mefuturesaibot.app.auth

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

// No production host is configured here. The caller must supply a trusted,
// deployment-controlled HTTPS origin, never a user-provided endpoint.
// Default platform TLS and hostname verification remain enabled.
internal class AuthenticationTransportSecurity(private val origin: HttpUrl) {
    init {
        require(origin.isHttps && origin.username.isEmpty() && origin.password.isEmpty())
        require(origin.encodedPath == "/" && origin.query == null && origin.fragment == null)
    }
    fun endpoint(vararg segments: String): HttpUrl {
        require(segments.isNotEmpty() && segments.all { it.matches(Regex("[A-Za-z0-9_-]{1,64}")) })
        val builder = origin.newBuilder()
        segments.forEach { builder.addPathSegment(it) }
        return builder.build()
    }
    fun client(): OkHttpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
}
