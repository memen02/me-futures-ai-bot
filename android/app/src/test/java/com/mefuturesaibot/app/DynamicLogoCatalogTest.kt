package com.mefuturesaibot.app

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DynamicLogoCatalogTest {

    @Test
    fun acceptsTrustedBinanceLogo() {
        val raw = """
            {
              "code":"000000",
              "data":[
                {
                  "assetCode":"NEW",
                  "logoUrl":"https://bin.bnbstatic.com/image/new.png"
                }
              ]
            }
        """.trimIndent()

        assertEquals(
            "/image/new.png",
            DynamicLogoCatalog.parse(raw)["NEW"]
        )
    }

    @Test
    fun rejectsUntrustedAndDelistedAssets() {
        val raw = """
            {
              "code":"000000",
              "data":[
                {
                  "assetCode":"BAD",
                  "logoUrl":"https://example.com/image/bad.png"
                },
                {
                  "assetCode":"HTTP",
                  "logoUrl":"http://bin.bnbstatic.com/image/a.png"
                },
                {
                  "assetCode":"OLD",
                  "delisted":true,
                  "logoUrl":"https://bin.bnbstatic.com/image/old.png"
                }
              ]
            }
        """.trimIndent()

        assertTrue(DynamicLogoCatalog.parse(raw).isEmpty())
    }

    @Test
    fun rejectsConflictingAssetLogos() {
        val raw = """
            {
              "code":"000000",
              "data":[
                {
                  "assetCode":"ABC",
                  "logoUrl":"https://bin.bnbstatic.com/image/a.png"
                },
                {
                  "assetCode":"ABC",
                  "logoUrl":"https://bin.bnbstatic.com/image/b.png"
                }
              ]
            }
        """.trimIndent()

        assertFalse(
            DynamicLogoCatalog.parse(raw).containsKey("ABC")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsFailedCatalogResponse() {
        DynamicLogoCatalog.parse(
            """{"code":"ERROR","data":[]}"""
        )
    }
}
