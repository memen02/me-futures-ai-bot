package com.mefuturesaibot.app.account

import com.mefuturesaibot.app.exchange.Signer
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class BinanceAccountCheckTest {
    private val pair = BinanceCredentials("example-key", "example-secret")

    private fun checker(answer: (Request) -> Pair<Int, String>) = BinanceAccountCheck(
        OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val (code, body) = answer(request)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(code).message("test").body(body.toResponseBody()).build()
        }.build()
    )

    @Test fun signsOnlyTheTwoPrivateReads() {
        val visited = mutableListOf<String>()
        val client = checker { request ->
            val url = request.url
            visited.add(url.host + url.encodedPath)
            assertEquals("https", url.scheme)
            assertEquals("GET", request.method)
            if (url.encodedPath.endsWith("/time")) {
                assertNull(request.header("X-MBX-APIKEY"))
                200 to """{"serverTime":1700000000000}"""
            } else {
                assertEquals(pair.key, request.header("X-MBX-APIKEY"))
                val query = url.encodedQuery!!.substringBefore("&signature=")
                assertEquals("5000", url.queryParameter("recvWindow"))
                assertTrue(url.queryParameter("timestamp")!!.toLong() >= 1700000000000L)
                assertEquals(Signer.hmacSha256Hex(query, pair.secret), url.queryParameter("signature"))
                assertFalse(url.toString().contains(pair.secret))
                if (url.encodedPath.endsWith("/accountConfig")) 200 to """{"canTrade":true}"""
                else 200 to """{"enableFutures":false,"enableWithdrawals":true}"""
            }
        }
        assertEquals(AccountAccess(true, false, true), client.verify(pair))
        assertEquals(listOf("fapi.binance.com/fapi/v1/time", "fapi.binance.com/fapi/v1/accountConfig",
            "api.binance.com/sapi/v1/account/apiRestrictions"), visited)
    }

    @Test fun distinguishesDeniedAccountFromUnavailableKeyPermissions() {
        fun mock(accountCode: Int) = checker { request ->
            when (request.url.encodedPath.substringAfterLast('/')) {
                "time" -> 200 to """{"serverTime":1700000000000}"""
                "accountConfig" -> if (accountCode == 200) 200 to """{"canTrade":false}"""
                    else accountCode to """{"code":-2015,"msg":"untrusted sample"}"""
                else -> 403 to "{}"
            }
        }
        assertEquals(AccountAccess(false, null, null), mock(200).verify(pair))
        val failure = assertThrows(AccountCheckError::class.java) { mock(403).verify(pair) }
        assertTrue(failure.explanation.contains("Akses ditolak"))
        assertFalse(failure.explanation.contains("untrusted sample"))
    }
}
